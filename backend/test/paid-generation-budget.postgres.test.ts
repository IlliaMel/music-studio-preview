import { randomUUID } from 'node:crypto'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { type DatabaseHandle, openDatabase } from '../src/core/db/database.js'
import { LibraryRepository } from '../src/features/library/library.repository.js'
import { uploads } from '../src/features/library/library.table.js'
import { paidGenerationDailyUsage } from '../src/features/tracks/paid-usage.table.js'
import { type NewTrack, TracksRepository } from '../src/features/tracks/tracks.repository.js'
import { tracks as trackTable } from '../src/features/tracks/tracks.table.js'
import { UsersRepository } from '../src/features/users/users.repository.js'
import { truncateAll } from './support/database.js'

// PGlite runs one transaction at a time, so its advisory locks never compete. Races need a real
// server on several connections: CI provides one through TEST_DATABASE_URL; without it, skip.
const url = process.env.TEST_DATABASE_URL

describe.skipIf(!url)('paid budget on a real Postgres', () => {
  let database: DatabaseHandle
  let repository: TracksRepository
  let owners: string[]
  const midnight = new Date('2026-10-22T00:00:00.000Z')
  const now = new Date('2026-10-22T12:00:00.000Z')
  const perUser = { since: midnight, max: 100 }

  const request = (userId: string): NewTrack => ({
    userId,
    idempotencyKey: randomUUID(),
    variants: 1,
    prompt: 'A new instrumental piano piece',
    lengthMs: 30000,
    instrumental: true,
    modelId: 'music_v2_5',
    providerName: 'elevenlabs',
  })

  beforeAll(async () => {
    database = await openDatabase({ kind: 'postgres', url: url ?? '' })
    await database.migrate()
    repository = new TracksRepository(database.db)
  })
  beforeEach(async () => {
    await truncateAll(database.db)
    const users = new UsersRepository(database.db)
    const identity = { email: null, emailVerified: false, signInProvider: 'dev' }
    owners = await Promise.all(
      Array.from({ length: 10 }, async (_, index) => {
        const user = await users.insertOrGet({ ...identity, firebaseUid: `race-${index}` }, now)
        return user.id
      }),
    )
  })
  afterAll(() => database?.close())

  it('reserves a shared paid upload exactly once under concurrent admission and rejection', async () => {
    const owner = owners[0] ?? ''
    const [upload] = await database.db
      .insert(uploads)
      .values({
        userId: owner,
        contentType: 'audio/mpeg',
        sizeBytes: 24,
        data: Buffer.from('ID3-test-reference').toString('base64'),
        createdAt: now,
      })
      .returning()
    if (!upload) throw new Error('Expected upload')
    const outcomes = await Promise.all(
      Array.from({ length: 10 }, () =>
        repository.create({ ...request(owner), referenceUploadId: upload.id }, perUser, now, {
          max: 4,
        }),
      ),
    )
    expect(outcomes.filter((value) => value.kind === 'created')).toHaveLength(3)
    expect(outcomes.filter((value) => value.kind === 'service-limit-reached')).toHaveLength(7)
    expect(await database.db.select().from(paidGenerationDailyUsage)).toEqual([
      { day: '2026-10-22', reservedVariants: 4 },
    ])
    const library = new LibraryRepository(database.db)
    const attemptId = randomUUID()
    expect(await library.claimRemoteUpload(upload.id, undefined, attemptId)).toBe(true)
    const results = await Promise.all([
      library.rejectRemoteUpload(upload.id, attemptId),
      library.rejectRemoteUpload(upload.id, attemptId),
      ...owners
        .slice(1)
        .map((userId) => repository.create(request(userId), perUser, now, { max: 4 })),
    ])
    const newAdmissions = results.filter((value) => value?.kind === 'created').length
    expect(newAdmissions).toBeLessThanOrEqual(1)
    expect(await database.db.select().from(paidGenerationDailyUsage)).toEqual([
      { day: '2026-10-22', reservedVariants: 3 + newAdmissions },
    ])
  })

  it('admits exactly the cap when ten owners race on separate connections', async () => {
    const outcomes = await Promise.all(
      owners.map((owner) => repository.create(request(owner), perUser, now, { max: 3 })),
    )

    expect(outcomes.filter((outcome) => outcome.kind === 'created')).toHaveLength(3)
    expect(outcomes.filter((outcome) => outcome.kind === 'service-limit-reached')).toHaveLength(7)
    expect(await database.db.select().from(paidGenerationDailyUsage)).toEqual([
      { day: '2026-10-22', reservedVariants: 3 },
    ])
    expect(await database.db.select().from(trackTable)).toHaveLength(3)
  })

  it('does not deadlock paid admission against a deletion request for the same owner', async () => {
    const [owner = ''] = owners
    const users = new UsersRepository(database.db)

    const [first, requested, second] = await Promise.all([
      repository.create(request(owner), perUser, now, { max: 3 }),
      users.requestDeletion(owner, now),
      repository.create(request(owner), perUser, now, { max: 3 }),
    ])

    expect(requested).toBe(true)
    for (const outcome of [first, second])
      expect(['created', 'account-deleting']).toContain(outcome.kind)
  })

  it('serializes duplicate refusals and competing admissions without double refunding or exceeding the cap', async () => {
    const owner = owners[0] ?? ''
    const created = await repository.create(request(owner), perUser, now, { max: 1 })
    if (created.kind !== 'created') throw new Error('Expected reservation')
    await repository.claimNext(now)
    const refusal = { code: 'QUOTA_EXCEEDED', message: 'Refused', promptSuggestion: null }
    const [first, duplicate, ...admissions] = await Promise.all([
      repository.fail(created.track.id, refusal, now),
      repository.fail(created.track.id, refusal, now),
      ...owners
        .slice(1)
        .map((userId) => repository.create(request(userId), perUser, now, { max: 1 })),
    ])
    expect([first, duplicate].filter(Boolean)).toHaveLength(1)
    const createdCount = admissions.filter(
      (outcome) => typeof outcome !== 'boolean' && outcome.kind === 'created',
    ).length
    expect(createdCount).toBeLessThanOrEqual(1)
    expect(await database.db.select().from(paidGenerationDailyUsage)).toEqual([
      { day: '2026-10-22', reservedVariants: createdCount },
    ])
  })
})
