import { randomUUID } from 'node:crypto'
import { eq } from 'drizzle-orm'
import { afterAll, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { catalogTracks } from '../src/features/library/catalog.js'
import { ElevenLabsMusicProvider } from '../src/features/tracks/generation/elevenlabs-music-provider.js'
import { MockMusicProvider } from '../src/features/tracks/generation/mock-music-provider.js'
import type { ComposeRequest } from '../src/features/tracks/generation/music-provider.js'
import { type NewTrack, TracksRepository } from '../src/features/tracks/tracks.repository.js'
import { TracksService } from '../src/features/tracks/tracks.service.js'
import { tracks } from '../src/features/tracks/tracks.table.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

let database: DatabaseHandle
beforeAll(async () => {
  database = await createTestDatabase()
})
beforeEach(() => truncateAll(database.db))
afterAll(() => database.close())
type App = Awaited<ReturnType<typeof createTestApp>>['app']
const ids = catalogTracks.slice(0, 4).map((track) => track.id)
const prompt = 'An original mellow electronic arrangement'
const onboard = (app: App, user = 'alice') =>
  app.request(
    '/v1/me',
    signedInAs(user, { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
const create = (app: App, body: Record<string, unknown>, key = randomUUID(), user = 'alice') =>
  app.request(
    '/v1/tracks',
    signedInAs(user, {
      method: 'POST',
      body: { prompt, ...body },
      headers: { 'Idempotency-Key': key },
    }),
  )
const json = async (response: Response) => (await response.json()) as Record<string, unknown>
const quota = async (app: App) => await json(await app.request('/v1/me/quota', signedInAs('alice')))
const original = async (test: Awaited<ReturnType<typeof createTestApp>>, user = 'alice') => {
  const row = await json(await create(test.app, {}, randomUUID(), user))
  await test.worker.runOnce()
  return String(row.id)
}

it('persists all ordered mock originals across worker restart and exposes honest capabilities without extra group credit', async () => {
  const seen: ComposeRequest[] = []
  class RecordingProvider extends MockMusicProvider {
    override async compose(request: ComposeRequest, signal: AbortSignal) {
      seen.push(request)
      return super.compose(request, signal)
    }
  }
  const first = await createTestApp(database.db)
  await onboard(first.app)
  expect(
    await json(await first.app.request('/v1/capabilities', signedInAs('alice'))),
  ).toMatchObject({ maxRemixSources: 4, remixOutput: 'fixture-no-audio-blending' })
  const response = await create(first.app, { remixTrackIds: ids, variants: 2 })
  expect(response.status).toBe(202)
  const created = await json(response)
  expect(created).toMatchObject({ remixTrackIds: ids, remixTrackId: ids[0] })
  const restarted = await createTestApp(database.db, {
    provider: new RecordingProvider(0),
    storage: () => first.storage,
  })
  await restarted.worker.runOnce()
  await restarted.worker.runOnce()
  expect(seen).toHaveLength(2)
  expect(seen.map((request) => request.remixTrackIds)).toEqual([ids, ids])
  expect(seen.every((request) => request.remixTrackId === ids[0])).toBe(true)
  const detail = await json(
    await restarted.app.request(`/v1/tracks/${created.id}`, signedInAs('alice')),
  )
  expect(detail).toMatchObject({ status: 'done', remixTrackIds: ids, genres: ['mock'] })
  expect(await quota(restarted.app)).toMatchObject({ used: 1, remaining: 7 })
})

it('normalizes legacy scalar retries to the same single-source identity and includes array order in idempotency', async () => {
  const { app } = await createTestApp(database.db)
  await onboard(app)
  const legacyKey = randomUUID()
  const first = await json(await create(app, { remixTrackId: ids[0] }, legacyKey))
  const replay = await create(app, { remixTrackIds: [ids[0]] }, legacyKey)
  expect(replay.status).toBe(200)
  expect((await json(replay)).id).toBe(first.id)
  await database.db
    .update(tracks)
    .set({ remixTrackIds: [] })
    .where(eq(tracks.id, String(first.id)))
  expect((await create(app, { remixTrackIds: [ids[0]] }, legacyKey)).status).toBe(200)
  expect(
    await json(await app.request(`/v1/tracks/${first.id}`, signedInAs('alice'))),
  ).toMatchObject({ remixTrackIds: [ids[0]], remixTrackId: ids[0] })
  const multiKey = randomUUID()
  expect((await create(app, { remixTrackIds: ids.slice(0, 2) }, multiKey)).status).toBe(202)
  expect((await create(app, { remixTrackIds: ids.slice(0, 2).reverse() }, multiKey)).status).toBe(
    422,
  )
  expect(await quota(app)).toMatchObject({ used: 2 })
})

it('rejects conflicting fields, duplicate sources, oversized arrays and uploaded-reference combinations before quota or queue', async () => {
  const { app, worker } = await createTestApp(database.db)
  await onboard(app)
  for (const body of [
    { remixTrackId: ids[0], remixTrackIds: [ids[0]] },
    { remixTrackId: ids[0], remixTrackIds: [] },
    { remixTrackIds: [ids[0], ids[0]] },
    { remixTrackIds: [...ids, catalogTracks[4]?.id] },
  ])
    expect((await create(app, body)).status).toBe(400)
  const conflict = await create(app, { remixTrackIds: [ids[0]], referenceUploadId: randomUUID() })
  expect(conflict.status).toBe(422)
  expect(await json(conflict)).toMatchObject({ error: { code: 'INVALID_REFERENCE' } })
  expect(await quota(app)).toMatchObject({ used: 0 })
  expect(await worker.runOnce()).toBe(false)
  expect((await create(app, { remixTrackIds: [] })).status).toBe(202)
})

it('checks every generated source for ownership, deletion, readiness and original-only status', async () => {
  const test = await createTestApp(database.db, { dailyLimit: 20 })
  await onboard(test.app)
  await onboard(test.app, 'bob')
  const owned = await original(test)
  const foreign = await original(test, 'bob')
  const deleted = await original(test)
  await test.app.request(`/v1/tracks/${deleted}`, signedInAs('alice', { method: 'DELETE' }))
  const remixed = await json(await create(test.app, { remixTrackIds: [owned] }))
  await test.worker.runOnce()
  const queued = await json(await create(test.app, {}))
  const baseline = (await quota(test.app)).used
  const cases: [string, number, string][] = [
    [foreign, 404, 'TRACK_NOT_FOUND'],
    [deleted, 404, 'TRACK_NOT_FOUND'],
    [String(queued.id), 409, 'NOT_READY'],
    [String(remixed.id), 409, 'REMIX_OF_REMIX'],
  ]
  for (const [id, status, code] of cases) {
    const result = await create(test.app, { remixTrackIds: [owned, id] })
    expect(result.status).toBe(status)
    expect(await json(result)).toMatchObject({ error: { code } })
  }
  expect((await quota(test.app)).used).toBe(baseline)
})

it('live adapter rejects multiple sources before quota, queue, reference resolution or paid HTTP', async () => {
  const fetcher = vi.fn<typeof fetch>()
  const resolve = vi.fn(async () => {
    throw new Error('No reference must be resolved')
  })
  const provider = new ElevenLabsMusicProvider({
    apiKey: 'test-key',
    references: true,
    fetch: fetcher,
    access: { resolve, claim: async () => true, save: async () => {} },
  })
  const { app, worker } = await createTestApp(database.db, { provider, dailyLimit: 1 })
  await onboard(app)
  expect(await json(await app.request('/v1/capabilities', signedInAs('alice')))).toMatchObject({
    maxRemixSources: 1,
  })
  const result = await create(app, { remixTrackIds: ids.slice(0, 2) })
  expect(result.status).toBe(422)
  expect(await json(result)).toMatchObject({ error: { code: 'UNSUPPORTED_MODE' } })
  expect(await quota(app)).toMatchObject({ used: 0, remaining: 1 })
  expect(await worker.runOnce()).toBe(false)
  expect(() =>
    provider.validate({
      prompt,
      instrumental: false,
      modelId: 'music_v2_5',
      remixTrackIds: ids.slice(0, 2),
    }),
  ).toThrow()
  expect(fetcher).not.toHaveBeenCalled()
  expect(resolve).not.toHaveBeenCalled()
})

it('advertises no live remix sources when reference entitlement is disabled', async () => {
  const provider = new ElevenLabsMusicProvider({
    apiKey: 'test-key',
    references: false,
    access: {
      resolve: async () => {
        throw new Error('unused')
      },
      claim: async () => false,
      save: async () => {},
    },
  })
  const { app } = await createTestApp(database.db, { provider })
  expect(await json(await app.request('/v1/capabilities', signedInAs('alice')))).toMatchObject({
    maxRemixSources: 0,
    audioReferences: 'disabled-enterprise-entitlement-required',
  })
})

it('rechecks all originals under SQL row locks when a source is deleted between service validation and insertion', async () => {
  const test = await createTestApp(database.db)
  const profile = await json(await onboard(test.app))
  const owned = await original(test)
  const removed = await original(test)
  const wake = vi.fn()
  class RacingRepository extends TracksRepository {
    override async create(input: NewTrack, limit: { since: Date; max: number }, now: Date) {
      await this.softDeleteForUser(input.userId, removed, now)
      return super.create(input, limit, now)
    }
  }
  const service = new TracksService(
    new RacingRepository(database.db),
    test.storage,
    { wake },
    { dailyLimit: 8, defaultModel: 'music_v2_5' },
    () => new Date(),
    new MockMusicProvider(0),
  )
  const baseline = (await quota(test.app)).used
  await expect(
    service.create(
      { id: String(profile.id), firebaseUid: 'alice', onboarded: true, deleting: false },
      randomUUID(),
      { prompt, instrumental: false, remixTrackIds: [owned, removed] },
    ),
  ).rejects.toMatchObject({ code: 'TRACK_NOT_FOUND' })
  expect(wake).not.toHaveBeenCalled()
  expect((await quota(test.app)).used).toBe(baseline)
})

it('single-track deletion clears every persisted remix source and preserves quota accounting', async () => {
  const { app } = await createTestApp(database.db)
  await onboard(app)
  const row = await json(await create(app, { remixTrackIds: ids.slice(0, 2) }))
  expect(
    (await app.request(`/v1/tracks/${row.id}`, signedInAs('alice', { method: 'DELETE' }))).status,
  ).toBe(204)
  const saved = (await database.db.select().from(tracks))[0]
  expect(saved?.remixTrackIds).toEqual([])
  expect(saved?.remixTrackId).toBeNull()
  expect(await quota(app)).toMatchObject({ used: 1 })
})
