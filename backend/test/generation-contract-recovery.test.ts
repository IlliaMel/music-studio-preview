import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, beforeAll, beforeEach, expect, it } from 'vitest'
import { composeApp } from '../src/compose.js'
import { DevAuthProvider } from '../src/core/auth/dev-auth-provider.js'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { LocalAudioStorage } from '../src/core/storage/audio-storage.js'
import { LibraryRepository } from '../src/features/library/library.repository.js'
import { uploads } from '../src/features/library/library.table.js'
import { ElevenLabsMusicProvider } from '../src/features/tracks/generation/elevenlabs-music-provider.js'
import { parseDetailed } from '../src/features/tracks/generation/elevenlabs-response.js'
import { GenerationWorker } from '../src/features/tracks/generation/generation-worker.js'
import { referenceAccess } from '../src/features/tracks/generation/reference-access.js'
import { paidGenerationDailyUsage } from '../src/features/tracks/paid-usage.table.js'
import { TracksRepository } from '../src/features/tracks/tracks.repository.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'
import { silentLogger } from './support/logger.js'

let database: DatabaseHandle
beforeAll(async () => {
  database = await createTestDatabase()
})
beforeEach(() => truncateAll(database.db))
afterAll(() => database.close())

const audio = Buffer.concat([Buffer.from('ID3'), Buffer.alloc(64)])
const chunk = { text: '[Verse]\nA new day', duration_ms: 30000, positive_styles: ['pop'] }
function detailed(type = 'audio/mpeg') {
  const meta = {
    composition_plan: { chunks: [chunk] },
    song_metadata: { title: 'Original', genres: ['pop'], is_explicit: false },
    words_timestamps: [],
  }
  const bytes = Buffer.concat([
    Buffer.from(
      `--fixture\r\nContent-Type: application/json\r\n\r\n${JSON.stringify(meta)}\r\n--fixture\r\nContent-Type: ${type}\r\n\r\n`,
    ),
    audio,
    Buffer.from('\r\n--fixture--\r\n'),
  ])
  return new Response(bytes, {
    headers: { 'Content-Type': 'multipart/mixed; boundary=fixture', 'song-id': 'song' },
  })
}
const uploaded = () =>
  Response.json({ song_id: 'reference-song', composition_plan: { chunks: [chunk] } })
const usage = async () =>
  (await database.db.select().from(paidGenerationDailyUsage))[0]?.reservedVariants ?? 0
const request = (app: Awaited<ReturnType<typeof setup>>['app'], key: string, extra = {}) =>
  app.request(
    '/v1/tracks',
    signedInAs('alice', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: { prompt: 'Warm acoustic piano and strings', lengthMs: 30000, ...extra },
    }),
  )
const track = async (app: Awaited<ReturnType<typeof setup>>['app'], id: string) =>
  (await app.request(`/v1/tracks/${id}`, signedInAs('alice'))).json()
async function setup(
  fetcher: typeof fetch,
  budget: number,
  fault: 'none' | 'twice' | 'after-write' = 'none',
) {
  let saves = 0
  const directory = await mkdtemp(join(tmpdir(), 'studio-contract-recovery-'))
  const storage = new (class extends LocalAudioStorage {
    override async save(key: string, bytes: Uint8Array) {
      saves++
      if (fault === 'twice' && saves < 3) throw new Error('Temporary disk failure')
      await super.save(key, bytes)
      if (fault === 'after-write') throw new Error('Lost save acknowledgement')
    }
  })(directory)
  const provider = new ElevenLabsMusicProvider({
    apiKey: 'test-key',
    references: true,
    fetch: fetcher,
    access: referenceAccess(database.db, budget),
  })
  const system = composeApp({
    db: database.db,
    log: silentLogger,
    auth: new DevAuthProvider(),
    storage,
    provider,
    tracks: {
      dailyLimit: 25,
      dailyPaidRequestsTotal: budget,
      defaultModel: 'music_v2_5',
      worker: { concurrency: 2, timeoutMs: 5000, retryDelaysMs: [], pollIntervalMs: 20 },
    },
  })
  await system.app.request(
    '/v1/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
  const upload = async () => {
    const response = await system.app.request('/v1/uploads', {
      method: 'POST',
      headers: { Authorization: 'Bearer dev:alice', 'Content-Type': 'audio/mpeg' },
      body: audio,
    })
    expect(response.status).toBe(201)
    return ((await response.json()) as { id: string }).id
  }
  return { ...system, storage, provider, upload, saves: () => saves }
}

it('reserves a new paid upload together with variants before admitting the generation', async () => {
  let calls = 0
  const system = await setup(async () => {
    calls++
    return detailed()
  }, 1)
  const referenceUploadId = await system.upload()
  expect((await request(system.app, 'reference-too-expensive', { referenceUploadId })).status).toBe(
    429,
  )
  expect(await usage()).toBe(0)
  expect((await database.db.select().from(uploads))[0]?.paidReservationDay).toBeNull()
  expect(await system.worker.runOnce()).toBe(false)
  expect(calls).toBe(0)
})

it('reserves a shared reference once across batches and does not upload the cached song twice', async () => {
  const paths: string[] = []
  const system = await setup(async (url) => {
    paths.push(new URL(String(url)).pathname)
    return String(url).includes('/upload') ? uploaded() : detailed()
  }, 3)
  const referenceUploadId = await system.upload()
  const accepted = await Promise.all([
    request(system.app, 'shared-reference-first', { referenceUploadId }),
    request(system.app, 'shared-reference-second', { referenceUploadId }),
  ])
  expect(accepted.map((r) => r.status)).toEqual([202, 202])
  expect(await usage()).toBe(3)
  expect(
    (await request(system.app, 'shared-reference-over-limit', { referenceUploadId })).status,
  ).toBe(429)
  await Promise.all([system.worker.runOnce(), system.worker.runOnce()])
  expect(paths.filter((p) => p === '/v1/music/upload')).toHaveLength(1)
  expect(paths.filter((p) => p === '/v1/music/detailed')).toHaveLength(2)
  expect(await usage()).toBe(3)
})

it('retains the spent upload reservation when later composition is refused', async () => {
  const system = await setup(
    async (url) =>
      String(url).includes('/upload')
        ? uploaded()
        : Response.json({ detail: { status: 'quota_exceeded' } }, { status: 402 }),
    2,
  )
  const referenceUploadId = await system.upload()
  const accepted = await request(system.app, 'upload-spent-compose-refused', { referenceUploadId })
  expect(accepted.status).toBe(202)
  await system.worker.runOnce()
  expect(await usage()).toBe(1)
  expect((await database.db.select().from(uploads))[0]?.providerSongId).toBe('reference-song')
})

it.each([401, 402, 403])(
  'a confirmed upload HTTP %s rejection resets only the unspent upload and supports recovery',
  async (status) => {
    let reject = true
    let calls = 0
    const system = await setup(async (url) => {
      calls++
      if (reject)
        return Response.json(
          { detail: { status: status === 402 ? 'quota_exceeded' : 'invalid_api_key' } },
          { status },
        )
      return String(url).includes('/upload') ? uploaded() : detailed()
    }, 2)
    const referenceUploadId = await system.upload()
    expect(
      (await request(system.app, 'confirmed-upload-rejection', { referenceUploadId })).status,
    ).toBe(202)
    await system.worker.runOnce()
    expect(await usage()).toBe(0)
    expect((await database.db.select().from(uploads))[0]).toMatchObject({
      providerAttempted: false,
      paidReservationDay: null,
    })
    reject = false
    const retry = await request(system.app, 'confirmed-upload-recovered', { referenceUploadId })
    expect(retry.status).toBe(202)
    const id = ((await retry.json()) as { id: string }).id
    await system.worker.runOnce()
    expect(await track(system.app, id)).toMatchObject({ status: 'done' })
    expect(calls).toBe(3)
    expect(await usage()).toBe(2)
  },
)

it('never releases or repeats an uncertain paid upload', async () => {
  let calls = 0
  const system = await setup(async () => {
    calls++
    throw new Error('Connection lost after upload')
  }, 2)
  const referenceUploadId = await system.upload()
  const accepted = await request(system.app, 'uncertain-upload-original', { referenceUploadId })
  const id = ((await accepted.json()) as { id: string }).id
  await system.worker.runOnce()
  expect(await track(system.app, id)).toMatchObject({
    status: 'failed',
    error: { code: 'PROVIDER_OUTCOME_UNKNOWN' },
  })
  expect((await database.db.select().from(uploads))[0]?.providerAttempted).toBe(true)
  expect(await usage()).toBe(2)
  expect(
    (await request(system.app, 'uncertain-upload-original', { referenceUploadId })).status,
  ).toBe(200)
  expect(await system.worker.runOnce()).toBe(false)
  expect(calls).toBe(1)
})

it('a late acknowledgement from an older upload cannot refund or overwrite the current attempt', async () => {
  const system = await setup(async () => {
    throw new Error('No upstream request expected')
  }, 1)
  const id = await system.upload()
  const library = new LibraryRepository(database.db)
  const first = crypto.randomUUID()
  const second = crypto.randomUUID()
  expect(await library.claimRemoteUpload(id, 1, first)).toBe(true)
  await library.rejectRemoteUpload(id, first)
  expect(await usage()).toBe(0)
  expect(await library.claimRemoteUpload(id, 1, second)).toBe(true)
  await library.rejectRemoteUpload(id, first)
  expect(await usage()).toBe(1)
  expect((await database.db.select().from(uploads))[0]).toMatchObject({
    providerAttempted: true,
    providerAttemptId: second,
  })
  await expect(library.saveRemoteUpload(id, 'old-result', 30000, first)).rejects.toThrow(
    'no longer current',
  )
  await library.saveRemoteUpload(id, 'current-result', 30000, second)
  await library.rejectRemoteUpload(id, second)
  expect(await usage()).toBe(1)
  expect((await database.db.select().from(uploads))[0]?.providerSongId).toBe('current-result')
})

it.each([false, true])(
  'a queued sibling re-reserves a rejected upload and respects competing usage: %s',
  async (competingUsage) => {
    let reject = true
    let calls = 0
    const system = await setup(async (url) => {
      calls++
      if (reject) return Response.json({ detail: { status: 'invalid_api_key' } }, { status: 401 })
      return String(url).includes('/upload') ? uploaded() : detailed()
    }, 3)
    const referenceUploadId = await system.upload()
    const accepted = await request(system.app, 'two-variants-upload-rejection', {
      referenceUploadId,
      variants: 2,
    })
    expect(accepted.status).toBe(202)
    const ids = ((await accepted.json()) as { tracks: { id: string }[] }).tracks.map(
      (value) => value.id,
    )
    expect(await usage()).toBe(3)
    await system.worker.runOnce()
    expect(await usage()).toBe(1)
    if (competingUsage) {
      expect((await request(system.app, 'competing-paid-work', { variants: 2 })).status).toBe(202)
      expect(await usage()).toBe(3)
    }
    reject = false
    await system.worker.runOnce()
    const results = await Promise.all(ids.map((id) => track(system.app, id)))
    if (competingUsage) {
      expect(results).toContainEqual(
        expect.objectContaining({
          status: 'failed',
          error: expect.objectContaining({ code: 'SERVICE_DAILY_LIMIT' }),
        }),
      )
      expect(calls).toBe(1)
    } else {
      expect(results).toContainEqual(expect.objectContaining({ status: 'done' }))
      expect(calls).toBe(3)
    }
    expect(await usage()).toBe(2)
  },
)

it('retries only persistence after temporary save failures and invokes paid generation once', async () => {
  let calls = 0
  const system = await setup(
    async () => {
      calls++
      return detailed()
    },
    1,
    'twice',
  )
  const accepted = await request(system.app, 'temporary-result-save-failure')
  const id = ((await accepted.json()) as { id: string }).id
  await system.worker.runOnce()
  expect(await track(system.app, id)).toMatchObject({ status: 'done' })
  expect(system.saves()).toBe(3)
  expect(calls).toBe(1)
})

it('retains paid audio and reports a non-retryable unknown result after permanent persistence failure', async () => {
  let calls = 0
  const system = await setup(
    async () => {
      calls++
      return detailed()
    },
    1,
    'after-write',
  )
  const accepted = await request(system.app, 'permanent-result-save-failure')
  const id = ((await accepted.json()) as { id: string }).id
  await system.worker.runOnce()
  expect(await track(system.app, id)).toMatchObject({
    status: 'failed',
    error: { code: 'PROVIDER_OUTCOME_UNKNOWN' },
  })
  expect(await system.storage.open(id)).toBeDefined()
  expect((await request(system.app, 'permanent-result-save-failure')).status).toBe(200)
  expect(await system.worker.runOnce()).toBe(false)
  expect(calls).toBe(1)
  expect(await usage()).toBe(1)
})

it('keeps audio when the result committed but its database acknowledgement was lost', async () => {
  let calls = 0
  const system = await setup(async () => {
    calls++
    return detailed()
  }, 1)
  class LostAcknowledgement extends TracksRepository {
    attempts = 0
    override async complete(...args: Parameters<TracksRepository['complete']>) {
      const saved = await super.complete(...args)
      if (++this.attempts === 1) throw new Error('Commit acknowledgement lost')
      return saved
    }
  }
  const repository = new LostAcknowledgement(database.db)
  const worker = new GenerationWorker(
    { repository, provider: system.provider, storage: system.storage, log: silentLogger },
    { concurrency: 1, timeoutMs: 5000, retryDelaysMs: [], pollIntervalMs: 20 },
  )
  const accepted = await request(system.app, 'lost-completion-acknowledgement')
  const id = ((await accepted.json()) as { id: string }).id
  await worker.runOnce()
  expect(await track(system.app, id)).toMatchObject({ status: 'done' })
  expect(await system.storage.open(id)).toBeDefined()
  expect(system.saves()).toBe(1)
  expect(repository.attempts).toBe(2)
  expect(calls).toBe(1)
})

it('accepts MIME parameters without weakening binary audio validation', async () => {
  const response = detailed('Audio/Mpeg; name="original.mp3"')
  const result = parseDetailed(
    response.headers.get('content-type') ?? '',
    Buffer.from(await response.arrayBuffer()),
    'song',
  )
  expect(result.audio).toEqual(audio)
})

it('rejects invalid lyric section names before reserving or calling the provider', async () => {
  let calls = 0
  const system = await setup(async () => {
    calls++
    return detailed()
  }, 1)
  expect(
    (
      await request(system.app, 'invalid-section-long-name', {
        customLyrics: `[${'x'.repeat(101)}]\nA new day`,
      })
    ).status,
  ).toBe(422)
  expect(
    (await request(system.app, 'invalid-section-empty-name', { customLyrics: '[]\nA new day' }))
      .status,
  ).toBe(422)
  expect(await usage()).toBe(0)
  expect(calls).toBe(0)
})
