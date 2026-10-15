import { once } from 'node:events'
import { createServer } from 'node:http'
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import {
  ElevenLabsMusicProvider,
  type ReferenceAccess,
} from '../src/features/tracks/generation/elevenlabs-music-provider.js'
import {
  boundedBody,
  parseDetailed,
} from '../src/features/tracks/generation/elevenlabs-response.js'
import { TracksRepository } from '../src/features/tracks/tracks.repository.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

const audio = Buffer.from([73, 68, 51, 0, 255, 0, 128, 10, 13])
const metadata = {
  composition_plan: { chunks: [{ duration_ms: 30000 }] },
  song_metadata: { title: 'Real fixture', genres: ['pop'], is_explicit: false },
  words_timestamps: [{ word: 'Hello', start_ms: 200, end_ms: 500 }],
}
function fixture(meta: unknown = metadata) {
  return Buffer.concat([
    Buffer.from(
      `--fixture\r\nContent-Type: application/json\r\n\r\n${JSON.stringify(meta)}\r\n--fixture\r\nContent-Type: audio/mpeg\r\n\r\n`,
    ),
    audio,
    Buffer.from('\r\n--fixture--\r\n'),
  ])
}
const response = () =>
  new Response(fixture(), {
    headers: { 'Content-Type': 'multipart/mixed; boundary="fixture"', 'song-id': 'song-fixture' },
  })
const request = {
  prompt: 'A warm acoustic pop song',
  modelId: 'music_v2_5' as const,
  instrumental: false,
  lengthMs: 30000,
  identity: { userId: 'user', trackId: 'track', generationId: 'group' },
}
const access: ReferenceAccess = {
  resolve: async () => {
    throw new Error('unexpected reference')
  },
  claim: async () => true,
  save: async () => {},
}
const provider = (fetcher: typeof fetch, references = false, reference = access) =>
  new ElevenLabsMusicProvider({ apiKey: 'test-key', references, access: reference, fetch: fetcher })

describe('ElevenLabs detailed transport', () => {
  it('sends the documented endpoint and headers through HTTP and preserves binary audio/timings', async () => {
    let received: unknown
    const server = createServer(async (req, res) => {
      const chunks = []
      for await (const chunk of req) chunks.push(Buffer.from(chunk))
      received = {
        url: req.url,
        key: req.headers['xi-api-key'],
        body: JSON.parse(Buffer.concat(chunks).toString()),
      }
      res.writeHead(200, {
        'Content-Type': 'multipart/mixed; boundary=fixture',
        'song-id': 'song-fixture',
      })
      res.end(fixture())
    })
    server.listen(0, '127.0.0.1')
    await once(server, 'listening')
    const address = server.address()
    if (!address || typeof address === 'string') throw new Error('No port')
    try {
      const live = new ElevenLabsMusicProvider({
        apiKey: 'test-key',
        references: false,
        access,
        baseUrl: `http://127.0.0.1:${address.port}`,
      })
      const result = await live.compose(request, new AbortController().signal)
      expect(result.audio).toEqual(audio)
      expect(result.words).toEqual([{ word: 'Hello', startMs: 200, endMs: 500 }])
      expect(result.songId).toBeNull()
      expect(received).toMatchObject({
        url: '/v1/music/detailed?output_format=mp3_44100_128',
        key: 'test-key',
        body: {
          prompt: request.prompt,
          model_id: 'music_v2_5',
          music_length_ms: 30000,
          with_timestamps: true,
          force_instrumental: false,
        },
      })
    } finally {
      await new Promise<void>((resolve, reject) =>
        server.close((error) => (error ? reject(error) : resolve())),
      )
    }
  })
  it('uses exact custom lyrics in v2 composition chunks with a stable per-track seed', async () => {
    const bodies: Record<string, unknown>[] = []
    const fetcher = vi.fn<typeof fetch>(async (_url, init) => {
      bodies.push(JSON.parse(String(init?.body)))
      return response()
    })
    const live = provider(fetcher)
    await live.compose({ ...request, customLyrics: 'Hello\nNew day' }, new AbortController().signal)
    await live.compose({ ...request, customLyrics: 'Hello\nNew day' }, new AbortController().signal)
    expect(bodies[0]).toEqual(bodies[1])
    expect(bodies[0]).toMatchObject({
      composition_plan: { chunks: [{ text: 'Hello\nNew day', duration_ms: 30000 }] },
    })
    expect(bodies[0]).not.toHaveProperty('prompt')
    expect(bodies[0]).not.toHaveProperty('force_instrumental')
  })
  it.each([401, 403, 422, 429, 500])(
    'classifies HTTP %s without exposing upstream content or retrying',
    async (status) => {
      const fetcher = vi.fn<typeof fetch>(
        async () => new Response('private-provider-key-and-lyrics', { status }),
      )
      await expect(
        provider(fetcher).compose(request, new AbortController().signal),
      ).rejects.toMatchObject({ retryable: false })
      expect(fetcher).toHaveBeenCalledTimes(1)
    },
  )
  it('marks connection loss as unknown instead of retrying a possibly accepted paid POST', async () => {
    const fetcher = vi.fn<typeof fetch>(async () => {
      throw new Error('private-network-detail')
    })
    await expect(
      provider(fetcher).compose(request, new AbortController().signal),
    ).rejects.toMatchObject({ code: 'PROVIDER_OUTCOME_UNKNOWN', retryable: false })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('rejects unsupported quality and reference entitlement before calling HTTP', async () => {
    const fetcher = vi.fn<typeof fetch>()
    await expect(
      provider(fetcher).compose({ ...request, quality: 'deep' }, new AbortController().signal),
    ).rejects.toMatchObject({ code: 'UNSUPPORTED_MODE' })
    await expect(
      provider(fetcher).compose(
        { ...request, referenceUploadId: 'upload' },
        new AbortController().signal,
      ),
    ).rejects.toMatchObject({ code: 'UNSUPPORTED_MODE' })
    expect(fetcher).not.toHaveBeenCalled()
  })
  it('uploads a reference once for parallel variants and persists its remote identity before composing', async () => {
    let remote: { songId: string; durationMs: number } | undefined
    const reference: ReferenceAccess = {
      resolve: async () => ({
        key: 'upload',
        audio: Buffer.concat([Buffer.from('ID3'), Buffer.alloc(20)]),
        contentType: 'audio/mpeg',
        ...remote,
      }),
      claim: vi.fn(async () => true),
      save: vi.fn(async (_source, songId, durationMs) => {
        remote = { songId, durationMs }
      }),
    }
    let uploads = 0
    const fetcher = vi.fn<typeof fetch>(async (url, init) => {
      if (String(url).includes('/upload')) {
        uploads++
        expect(init?.body).toBeInstanceOf(FormData)
        expect(
          (init?.body instanceof FormData ? init.body : new FormData()).get(
            'extract_composition_plan',
          ),
        ).toBe('music_v2_5')
        return Response.json({
          song_id: 'reference-song',
          composition_plan: { chunks: [{ duration_ms: 30000 }] },
        })
      }
      const body = JSON.parse(String(init?.body))
      expect(body.composition_plan.chunks[0].conditioning_ref).toEqual({
        song_id: 'reference-song',
        range: { start_ms: 0, end_ms: 30000 },
      })
      expect(remote).toBeDefined()
      return response()
    })
    const live = provider(fetcher, true, reference)
    await Promise.all([
      live.compose({ ...request, referenceUploadId: 'upload' }, new AbortController().signal),
      live.compose(
        { ...request, referenceUploadId: 'upload', variantIndex: 1 },
        new AbortController().signal,
      ),
    ])
    expect(uploads).toBe(1)
    await live.compose({ ...request, referenceUploadId: 'upload' }, new AbortController().signal)
    expect(uploads).toBe(1)
  })
  it('does not resubmit a reference with an uncertain durable upload marker', async () => {
    const fetcher = vi.fn<typeof fetch>()
    const reference: ReferenceAccess = {
      ...access,
      resolve: async () => ({
        key: 'upload',
        audio: Buffer.concat([Buffer.from('ID3'), Buffer.alloc(20)]),
        contentType: 'audio/mpeg',
        attempted: true,
      }),
    }
    await expect(
      provider(fetcher, true, reference).compose(
        { ...request, referenceUploadId: 'upload' },
        new AbortController().signal,
      ),
    ).rejects.toMatchObject({ code: 'PROVIDER_OUTCOME_UNKNOWN' })
    expect(fetcher).not.toHaveBeenCalled()
  })
  it('rejects malformed multipart, invalid timestamps and oversized streaming bodies', async () => {
    expect(() =>
      parseDetailed('multipart/mixed; boundary=fixture', fixture().subarray(0, 100), null),
    ).toThrow()
    expect(() =>
      parseDetailed(
        'multipart/mixed; boundary=fixture',
        fixture({ ...metadata, words_timestamps: [{ word: 'Bad', start_ms: 900, end_ms: 100 }] }),
        null,
      ),
    ).toThrow()
    await expect(boundedBody(new Response(new Uint8Array(20)), 10)).rejects.toThrow()
  })
})

let database: DatabaseHandle
beforeAll(async () => {
  database = await createTestDatabase()
})
beforeEach(() => truncateAll(database.db))
afterAll(() => database.close())
it('durably fails an uncertain paid generation and does not enqueue a second paid attempt on HTTP retry', async () => {
  const fetcher = vi.fn<typeof fetch>(async () => {
    throw new Error('accepted then disconnected')
  })
  const { app, worker } = await createTestApp(database.db, { provider: provider(fetcher) })
  await app.request(
    '/v1/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
  const options = signedInAs('alice', {
    method: 'POST',
    body: { prompt: request.prompt },
    headers: { 'Idempotency-Key': 'paid-unknown-attempt' },
  })
  const first = await app.request('/v1/tracks', options)
  const track = (await first.json()) as { id: string }
  await worker.runOnce()
  expect(await worker.runOnce()).toBe(false)
  const retry = await app.request('/v1/tracks', options)
  expect(retry.status).toBe(200)
  expect(await retry.json()).toMatchObject({
    id: track.id,
    status: 'failed',
    error: { code: 'PROVIDER_OUTCOME_UNKNOWN' },
  })
  expect(fetcher).toHaveBeenCalledTimes(1)
})
it('recovers crashed paid rows as unknown without reopening a paid attempt', async () => {
  const { app } = await createTestApp(database.db, { provider: provider(vi.fn<typeof fetch>()) })
  await app.request(
    '/v1/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
  await app.request(
    '/v1/tracks',
    signedInAs('alice', {
      method: 'POST',
      body: { prompt: request.prompt },
      headers: { 'Idempotency-Key': 'paid-crash-attempt' },
    }),
  )
  const repository = new TracksRepository(database.db)
  const claimed = await repository.claimNext(new Date(0))
  expect(claimed?.providerName).toBe('elevenlabs')
  await repository.failAbandoned(
    new Date(1000),
    { code: 'INTERRUPTED', message: 'crash', promptSuggestion: null },
    new Date(),
  )
  const result = await app.request(`/v1/tracks/${claimed?.id}`, signedInAs('alice'))
  expect(await result.json()).toMatchObject({
    status: 'failed',
    error: { code: 'PROVIDER_OUTCOME_UNKNOWN' },
  })
})

it('creates two real provider songs for one local group credit and rejects unsupported options before queueing', async () => {
  const fetcher = vi.fn<typeof fetch>(async () => response())
  const { app, worker } = await createTestApp(database.db, {
    provider: provider(fetcher),
    dailyLimit: 1,
  })
  await app.request(
    '/v1/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
  const bad = await app.request(
    '/v1/tracks',
    signedInAs('alice', {
      method: 'POST',
      body: { prompt: request.prompt, quality: 'fast' },
      headers: { 'Idempotency-Key': 'unsupported-live-mode' },
    }),
  )
  expect(bad.status).toBe(422)
  expect(await worker.runOnce()).toBe(false)
  const create = await app.request(
    '/v1/tracks',
    signedInAs('alice', {
      method: 'POST',
      body: { prompt: request.prompt, variants: 2 },
      headers: { 'Idempotency-Key': 'live-both-variants' },
    }),
  )
  expect(create.status).toBe(202)
  await worker.runOnce()
  await worker.runOnce()
  expect(fetcher).toHaveBeenCalledTimes(2)
  expect(await (await app.request('/v1/me/quota', signedInAs('alice'))).json()).toMatchObject({
    used: 1,
    remaining: 0,
  })
  const invalid = await app.request('/v1/uploads', {
    method: 'POST',
    headers: { Authorization: 'Bearer dev:alice', 'Content-Type': 'audio/mp4' },
    body: new Uint8Array([1, 2, 3]),
  })
  expect(invalid.status).toBe(422)
})
it('never requeues a paid POST aborted by server shutdown', async () => {
  let entered: () => void = () => {}
  const started = new Promise<void>((resolve) => {
    entered = resolve
  })
  const fetcher = vi.fn<typeof fetch>(
    async (_url, options) =>
      new Promise<Response>((_resolve, reject) => {
        entered()
        options?.signal?.addEventListener(
          'abort',
          () => reject(new Error('remote result unknown')),
          { once: true },
        )
      }),
  )
  const { app, worker } = await createTestApp(database.db, { provider: provider(fetcher) })
  await app.request(
    '/v1/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
  const created = await app.request(
    '/v1/tracks',
    signedInAs('alice', {
      method: 'POST',
      body: { prompt: request.prompt },
      headers: { 'Idempotency-Key': 'paid-shutdown-attempt' },
    }),
  )
  const track = (await created.json()) as { id: string }
  worker.start()
  await started
  await worker.stop()
  expect(await worker.runOnce()).toBe(false)
  expect(
    await (await app.request(`/v1/tracks/${track.id}`, signedInAs('alice'))).json(),
  ).toMatchObject({ status: 'failed', error: { code: 'PROVIDER_OUTCOME_UNKNOWN' } })
  expect(fetcher).toHaveBeenCalledTimes(1)
})

it('maps documented insufficient quota and bad-prompt suggestions without exposing upstream error messages', async () => {
  const quota = provider(
    vi.fn<typeof fetch>(async () =>
      Response.json(
        { detail: { status: 'quota_exceeded', message: 'private upstream body' } },
        { status: 401 },
      ),
    ),
  )
  await expect(quota.compose(request, new AbortController().signal)).rejects.toMatchObject({
    code: 'QUOTA_EXCEEDED',
    message: 'ElevenLabs account has insufficient credits',
    retryable: false,
  })
  const prompt = provider(
    vi.fn<typeof fetch>(async () =>
      Response.json(
        {
          detail: {
            status: 'bad_prompt',
            message: 'private upstream body',
            data: { prompt_suggestion: 'A calm instrumental' },
          },
        },
        { status: 400 },
      ),
    ),
  )
  await expect(prompt.compose(request, new AbortController().signal)).rejects.toMatchObject({
    code: 'INVALID_PROMPT',
    promptSuggestion: 'A calm instrumental',
  })
})
