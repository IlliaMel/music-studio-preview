import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { eq, sql } from 'drizzle-orm'
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import type { AudioStorage } from '../src/core/storage/audio-storage.js'
import {
  type MusicProvider,
  ProviderError,
} from '../src/features/tracks/generation/music-provider.js'
import { paidGenerationDailyUsage } from '../src/features/tracks/paid-usage.table.js'
import { type NewTrack, TracksRepository } from '../src/features/tracks/tracks.repository.js'
import { TracksService } from '../src/features/tracks/tracks.service.js'
import { tracks as trackTable } from '../src/features/tracks/tracks.table.js'
import { UsersRepository } from '../src/features/users/users.repository.js'
import { createTestDatabase, truncateAll } from './support/database.js'

let database: DatabaseHandle
let repository: TracksRepository
let ownerA: string
let ownerB: string
const now = new Date('2026-10-22T23:59:59.999Z')
const midnight = new Date('2026-10-22T00:00:00.000Z')
const perUser = { since: midnight, max: 100 }

beforeAll(async () => {
  database = await createTestDatabase()
  repository = new TracksRepository(database.db)
})
beforeEach(async () => {
  await truncateAll(database.db)
  const users = new UsersRepository(database.db)
  const identity = { email: null, emailVerified: false, signInProvider: 'dev' }
  ownerA = (await users.insertOrGet({ ...identity, firebaseUid: 'budget-owner-a' }, now)).id
  ownerB = (await users.insertOrGet({ ...identity, firebaseUid: 'budget-owner-b' }, now)).id
})
afterAll(() => database.close())

const request = (
  userId: string,
  idempotencyKey: string = randomUUID(),
  variants = 1,
): NewTrack => ({
  userId,
  idempotencyKey,
  variants,
  prompt: 'A new instrumental piano piece',
  lengthMs: 30000,
  instrumental: true,
  modelId: 'music_v2_5',
  providerName: 'elevenlabs',
})
const usage = () => database.db.select().from(paidGenerationDailyUsage)

// PGlite serializes transactions, so this checks the bookkeeping, not lock contention; the real
// race runs in paid-generation-budget.postgres.test.ts.
it('admits exactly one of two owners asking for the final paid variant', async () => {
  const outcomes = await Promise.all([
    repository.create(request(ownerA), perUser, now, { max: 1 }),
    new TracksRepository(database.db).create(request(ownerB), perUser, now, { max: 1 }),
  ])
  expect(outcomes.map((value) => value.kind).sort()).toEqual(['created', 'service-limit-reached'])
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
  expect(await database.db.select().from(trackTable)).toHaveLength(1)
})

it('reserves both variants atomically and does not refund an idempotent replay at the limit', async () => {
  const input = request(ownerA, 'two-variants', 2)
  const created = await repository.create(input, perUser, now, { max: 2 })
  expect(created.kind).toBe('created')
  expect(await repository.create(input, perUser, now, { max: 2 })).toMatchObject({
    kind: 'existing',
  })
  expect(await repository.create(request(ownerB), perUser, now, { max: 2 })).toEqual({
    kind: 'service-limit-reached',
    used: 2,
  })
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 2 }])
  expect(await database.db.select().from(trackTable)).toHaveLength(2)
})

it('rejects a two-variant batch when only one slot is left without partially creating or reserving it', async () => {
  await repository.create(request(ownerA), perUser, now, { max: 2 })
  expect(await repository.create(request(ownerB, 'too-many', 2), perUser, now, { max: 2 })).toEqual(
    { kind: 'service-limit-reached', used: 1 },
  )
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
  expect(await repository.findByKey(ownerB, 'too-many')).toBeUndefined()
})

it.each([
  'INVALID_PROMPT',
  'RATE_LIMITED',
  'PROVIDER_UNAVAILABLE',
  'PROVIDER_OUTCOME_UNKNOWN',
  'TIMEOUT',
  'INTERNAL',
])('never refunds a %s failure, soft deletion, or subsequent account purge', async (code) => {
  const created = await repository.create(request(ownerA), perUser, now, { max: 1 })
  if (created.kind !== 'created') throw new Error('Expected a queued reservation')
  await repository.claimNext(now)
  expect(
    await repository.fail(created.track.id, { code, message: code, promptSuggestion: null }, now),
  ).toBe(true)
  expect(await repository.softDeleteForUser(ownerA, created.track.id, now)).toBe(true)
  await new UsersRepository(database.db).delete(ownerA)
  expect(await database.db.select().from(trackTable)).toEqual([])
  expect(await repository.create(request(ownerB), perUser, now, { max: 1 })).toEqual({
    kind: 'service-limit-reached',
    used: 1,
  })
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
})

it.each(['QUOTA_EXCEEDED', 'PROVIDER_AUTH'])(
  'returns the user credit and exactly one paid variant for a confirmed %s refusal',
  async (code) => {
    const input = request(ownerA, 'refused')
    const created = await repository.create(input, { ...perUser, max: 1 }, now, { max: 1 })
    if (created.kind !== 'created') throw new Error('Expected reservation')
    await repository.claimNext(now)
    const failure = { code, message: 'Provider refused', promptSuggestion: null }
    const nextDay = new Date('2026-10-23T00:00:01Z')
    expect(await repository.fail(created.track.id, failure, nextDay)).toBe(true)
    expect(await repository.fail(created.track.id, failure, nextDay)).toBe(false)
    expect(await repository.countTowardLimit(ownerA, midnight)).toBe(0)
    expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 0 }])
    // Replaying the rejected attempt returns its failure and never invokes a second generation.
    expect((await repository.create(input, perUser, now, { max: 1 })).kind).toBe('existing')
    expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 0 }])
    expect(
      (await repository.create(request(ownerA), { ...perUser, max: 1 }, now, { max: 1 })).kind,
    ).toBe('created')
  },
)

it('returns a group credit only after every variant was definitively refused', async () => {
  await repository.create(request(ownerA, 'refused-group', 2), perUser, now, { max: 2 })
  const first = await repository.claimNext(now)
  const second = await repository.claimNext(now)
  if (!first || !second) throw new Error('Expected both variants')
  const failure = { code: 'QUOTA_EXCEEDED', message: 'No credits', promptSuggestion: null }
  await repository.fail(first.id, failure, now)
  expect(await repository.countTowardLimit(ownerA, midnight)).toBe(1)
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
  await repository.fail(second.id, failure, now)
  expect(await repository.countTowardLimit(ownerA, midnight)).toBe(0)
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 0 }])
})

it('retains the group credit and other paid variant when its sibling has an uncertain outcome', async () => {
  await repository.create(request(ownerA, 'mixed-group', 2), perUser, now, { max: 2 })
  const first = await repository.claimNext(now)
  const second = await repository.claimNext(now)
  if (!first || !second) throw new Error('Expected both variants')
  await repository.fail(
    first.id,
    { code: 'PROVIDER_AUTH', message: 'Refused', promptSuggestion: null },
    now,
  )
  await repository.fail(
    second.id,
    { code: 'PROVIDER_OUTCOME_UNKNOWN', message: 'Unknown', promptSuggestion: null },
    now,
  )
  expect(await repository.countTowardLimit(ownerA, midnight)).toBe(1)
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
})

it('refunds a late definitive rejection after track deletion without restoring erased content', async () => {
  const result = await repository.create(request(ownerA), perUser, now, { max: 1 })
  if (result.kind !== 'created') throw new Error('Expected reservation')
  await repository.claimNext(now)
  await repository.softDeleteForUser(ownerA, result.track.id, now)
  await repository.fail(
    result.track.id,
    {
      code: 'QUOTA_EXCEEDED',
      message: 'Private provider message',
      promptSuggestion: 'Private suggestion',
    },
    now,
  )
  expect(await repository.findForUser(ownerA, result.track.id, true)).toMatchObject({
    status: 'failed',
    prompt: '',
    errorMessage: null,
    promptSuggestion: null,
  })
  expect(await repository.countTowardLimit(ownerA, midnight)).toBe(0)
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 0 }])
  await new UsersRepository(database.db).delete(ownerA)
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 0 }])
})

it('does not release another paid reservation for an unpaid adapter with an ElevenLabs name', async () => {
  await repository.create(request(ownerA), perUser, now, { max: 1 })
  await repository.claimNext(now)
  const unpaid = await repository.create(request(ownerB), perUser, now)
  if (unpaid.kind !== 'created') throw new Error('Expected unpaid fixture')
  await repository.claimNext(now)
  await repository.fail(
    unpaid.track.id,
    { code: 'QUOTA_EXCEEDED', message: 'Refused', promptSuggestion: null },
    now,
  )
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
})

it('retains the reservation when account purge removed the row before the refusal was recorded', async () => {
  const result = await repository.create(request(ownerA), perUser, now, { max: 1 })
  if (result.kind !== 'created') throw new Error('Expected reservation')
  await repository.claimNext(now)
  await new UsersRepository(database.db).delete(ownerA)
  expect(
    await repository.fail(
      result.track.id,
      { code: 'PROVIDER_AUTH', message: 'Refused', promptSuggestion: null },
      now,
    ),
  ).toBe(false)
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
})

it('conservatively retains a legacy reservation whose admission provenance is unknown', async () => {
  const result = await repository.create(request(ownerA), perUser, now, { max: 1 })
  if (result.kind !== 'created') throw new Error('Expected reservation')
  // Existing pre-migration rows cannot prove whether their adapter reserved a paid unit.
  await database.db
    .update(trackTable)
    .set({ paidReservationDay: null })
    .where(eq(trackTable.id, result.track.id))
  await repository.claimNext(now)
  await repository.fail(
    result.track.id,
    { code: 'QUOTA_EXCEEDED', message: 'Refused', promptSuggestion: null },
    now,
  )
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
})

it('starts a fresh budget precisely at UTC midnight and replaying yesterday does not consume today', async () => {
  const input = request(ownerA, 'before-midnight')
  await repository.create(input, perUser, now, { max: 1 })
  const next = new Date('2026-10-23T00:00:00.000Z')
  const nextLimit = { since: next, max: 100 }
  expect((await repository.create(input, nextLimit, next, { max: 1 })).kind).toBe('existing')
  expect((await repository.create(request(ownerB), nextLimit, next, { max: 1 })).kind).toBe(
    'created',
  )
  expect((await repository.create(request(ownerA), nextLimit, next, { max: 1 })).kind).toBe(
    'service-limit-reached',
  )
  expect((await usage()).sort((a, b) => a.day.localeCompare(b.day))).toEqual([
    { day: '2026-10-22', reservedVariants: 1 },
    { day: '2026-10-23', reservedVariants: 1 },
  ])
})

it('does not reserve for an invalid upload, unavailable remix source, deleted owner, or user quota refusal', async () => {
  expect(
    (
      await repository.create(
        { ...request(ownerA), referenceUploadId: randomUUID() },
        perUser,
        now,
        { max: 1 },
      )
    ).kind,
  ).toBe('upload-not-found')
  expect(
    (
      await repository.create({ ...request(ownerA), remixTrackIds: [randomUUID()] }, perUser, now, {
        max: 1,
      })
    ).kind,
  ).toBe('invalid-remix-source')
  expect(
    (await repository.create(request(ownerA), { ...perUser, max: 0 }, now, { max: 1 })).kind,
  ).toBe('limit-reached')
  await new UsersRepository(database.db).requestDeletion(ownerA, now)
  expect((await repository.create(request(ownerA), perUser, now, { max: 1 })).kind).toBe(
    'account-deleting',
  )
  expect(await usage()).toEqual([])
})

it('rolls a reservation back when the track insert fails after admission', async () => {
  await repository.create(request(ownerA, 'collision:variant:1'), perUser, now, { max: 3 })
  await expect(
    repository.create(request(ownerA, 'collision', 2), perUser, now, { max: 3 }),
  ).rejects.toThrow()
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
  expect(await repository.findByKey(ownerA, 'collision')).toBeUndefined()
  expect(
    (await repository.create(request(ownerB, 'after-rollback', 2), perUser, now, { max: 3 })).kind,
  ).toBe('created')
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 3 }])
})

it('migration backfills existing paid variant rows across UTC days, including failed and deleted rows', async () => {
  const historical = await repository.create(request(ownerA, 'historical-paid', 2), perUser, now)
  if (historical.kind !== 'created') throw new Error('Expected historical variants')
  await repository.claimNext(now)
  await repository.fail(
    historical.track.id,
    { code: 'INVALID_PROMPT', message: 'fixture', promptSuggestion: null },
    now,
  )
  await repository.softDeleteForUser(ownerA, historical.track.id, now)
  const yesterday = new Date('2026-10-21T23:59:59.999Z')
  await repository.create(
    request(ownerB, 'yesterday-paid'),
    { since: yesterday, max: 100 },
    yesterday,
  )
  await repository.create(
    { ...request(ownerB, 'historical-mock'), providerName: 'mock' },
    perUser,
    now,
  )
  const migration = await readFile(
    new URL('../drizzle/0008_paid_generation_daily_usage.sql', import.meta.url),
    'utf8',
  )
  await database.db.transaction(async (tx) => {
    await tx.execute(sql`DROP TABLE paid_generation_daily_usage`)
    for (const statement of migration.split('--> statement-breakpoint'))
      await tx.execute(sql.raw(statement))
  })
  expect((await usage()).sort((a, b) => a.day.localeCompare(b.day))).toEqual([
    { day: '2026-10-21', reservedVariants: 1 },
    { day: '2026-10-22', reservedVariants: 2 },
  ])
  await new UsersRepository(database.db).delete(ownerA)
  expect(
    (await repository.create(request(ownerB, 'after-migration'), perUser, now, { max: 2 })).kind,
  ).toBe('service-limit-reached')
})

it('keeps completed reservations and allows a configured zero budget as an explicit paid shutdown', async () => {
  expect((await repository.create(request(ownerA), perUser, now, { max: 0 })).kind).toBe(
    'service-limit-reached',
  )
  expect(await usage()).toEqual([])
  const created = await repository.create(request(ownerA), perUser, now, { max: 1 })
  if (created.kind !== 'created') throw new Error('Expected a queued reservation')
  await repository.claimNext(now)
  expect(
    await repository.complete(
      created.track.id,
      {
        title: 'Complete',
        durationMs: 30000,
        genres: [],
        explicit: false,
        words: [],
        songId: null,
      },
      now,
    ),
  ).toBe(true)
  expect((await repository.create(request(ownerB), perUser, now, { max: 1 })).kind).toBe(
    'service-limit-reached',
  )
  expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
})

const storage: AudioStorage = {
  save: async () => {},
  open: async () => undefined,
  delete: async () => {},
}
const provider = (paid: boolean | undefined, name = 'paid-fixture'): MusicProvider => ({
  name,
  paid,
  compose: async () => {
    throw new Error('No provider calls are allowed in admission tests')
  },
})
const service = (paid: MusicProvider, max?: number, wake = vi.fn()) =>
  new TracksService(
    repository,
    storage,
    { wake },
    { dailyLimit: 100, defaultModel: 'music_v2_5', dailyPaidRequestsTotal: max },
    () => now,
    paid,
  )
const user = () => ({
  id: ownerA,
  firebaseUid: 'budget-owner-a',
  onboarded: true,
  canGenerate: true,
})
const body = { prompt: 'A gentle cinematic piano introduction', instrumental: true }

describe('paid provider service admission', () => {
  it('returns the stable service 429 and UTC reset without waking the worker or spending on replays', async () => {
    const wake = vi.fn()
    const live = service(provider(true), 1, wake)
    await live.create(user(), 'first', body)
    await expect(live.create(user(), 'second', body)).rejects.toMatchObject({
      status: 429,
      code: 'SERVICE_DAILY_LIMIT',
      details: { limit: 1, resetsAt: '2026-10-23T00:00:00.000Z' },
    })
    expect(await live.create(user(), 'first', body)).toMatchObject({ created: false })
    expect(wake).toHaveBeenCalledTimes(1)
    expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 1 }])
  })
  it('preserves unlimited paid admission when the new option is absent but retains usage for later enforcement', async () => {
    const live = service(provider(true))
    await live.create(user(), 'unlimited-a', body)
    await live.create(user(), 'unlimited-b', body)
    await expect(
      service(provider(true), 2).create(user(), 'new-limited', body),
    ).rejects.toMatchObject({ code: 'SERVICE_DAILY_LIMIT' })
    expect(await usage()).toEqual([{ day: '2026-10-22', reservedVariants: 2 }])
  })
  it.each([provider(undefined, 'mock'), provider(false, 'elevenlabs')])(
    'never applies the paid budget to an unpaid adapter, including a misleading provider name',
    async (unpaid) => {
      const mock = service(unpaid, 0)
      await expect(mock.create(user(), 'mock-a', body)).resolves.toMatchObject({ created: true })
      await expect(mock.create(user(), 'mock-b', body)).resolves.toMatchObject({ created: true })
      expect(await usage()).toEqual([])
    },
  )
  it('rejects invalid provider input before reserving any budget', async () => {
    const invalid = {
      ...provider(true),
      validate: () => {
        throw new ProviderError('INVALID_PROMPT', 'Rejected fixture', false)
      },
    }
    await expect(service(invalid, 1).create(user(), 'invalid', body)).rejects.toMatchObject({
      code: 'INVALID_PROMPT',
    })
    expect(await usage()).toEqual([])
  })
})
