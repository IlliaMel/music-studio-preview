import { readdir } from 'node:fs/promises'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import type { ErrorBody } from '../src/core/http/errors.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

type TrackJson = {
  id: string
  status: string
  title: string | null
  audioUrl: string | null
  words?: { word: string }[]
  error: { code: string; promptSuggestion: string | null } | null
}

let database: DatabaseHandle
let testApp: Awaited<ReturnType<typeof createTestApp>>
let keys = 0

beforeAll(async () => {
  database = await createTestDatabase()
  testApp = await createTestApp(database.db, { dailyLimit: 3 })
})

beforeEach(() => truncateAll(database.db))

afterAll(() => database.close())

const request = (path: string, init: RequestInit) => testApp.app.request(path, init)

async function onboard(name: string) {
  await request(
    '/v1/me',
    signedInAs(name, {
      method: 'PATCH',
      body: { displayName: name, ageConfirmed: true, acceptedTermsVersion: TERMS_VERSION },
    }),
  )
}

function createTrack(name: string, body: unknown, key = `key-${++keys}-attempt`) {
  return request(
    '/v1/tracks',
    signedInAs(name, { method: 'POST', body, headers: { 'Idempotency-Key': key } }),
  )
}

const json = async <T>(response: Response) => (await response.json()) as T
const errorCode = async (response: Response) => (await json<ErrorBody>(response)).error.code

const SONG = { prompt: 'Dreamy synth-pop about a night drive through Kyiv', lengthMs: 30_000 }

describe('creating a track', () => {
  it('needs a finished onboarding', async () => {
    const response = await createTrack('alice', SONG)

    expect(response.status).toBe(403)
    expect(await errorCode(response)).toBe('ONBOARDING_REQUIRED')
  })

  it('needs an idempotency key', async () => {
    await onboard('alice')

    const response = await request(
      '/v1/tracks',
      signedInAs('alice', { method: 'POST', body: SONG }),
    )

    expect(response.status).toBe(400)
  })

  it.each([
    ['a prompt shorter than 10 characters', { prompt: 'Short' }],
    ['a length under 30 seconds', { ...SONG, lengthMs: 10_000 }],
    ['an unknown model', { ...SONG, modelId: 'music_v9' }],
  ])('rejects %s', async (_, body) => {
    await onboard('alice')

    expect((await createTrack('alice', body)).status).toBe(400)
  })

  it('queues the generation and answers at once', async () => {
    await onboard('alice')

    const response = await createTrack('alice', SONG)
    const track = await json<TrackJson>(response)

    expect(response.status).toBe(202)
    expect(response.headers.get('Location')).toBe(`/v1/tracks/${track.id}`)
    expect(track).toMatchObject({ status: 'queued', audioUrl: null, error: null })
  })

  it('returns the same track when the app retries with the same key', async () => {
    await onboard('alice')

    const first = await json<TrackJson>(await createTrack('alice', SONG, 'retry-key-1'))
    const retry = await createTrack('alice', SONG, 'retry-key-1')

    expect(retry.status).toBe(200)
    expect((await json<TrackJson>(retry)).id).toBe(first.id)
  })
})

describe('a generated track', () => {
  it('has a title, lyrics with timings and a playable audio link', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(await createTrack('alice', SONG))

    await testApp.worker.runOnce()
    const track = await json<TrackJson>(await request(`/v1/tracks/${id}`, signedInAs('alice')))

    expect(track).toMatchObject({
      status: 'done',
      title: 'Dreamy Synth-pop About A',
      audioUrl: `https://api.studio.test/v1/tracks/${id}/audio`,
    })
    expect(track.words?.length).toBeGreaterThan(10)
  })

  it('has no lyrics when it is instrumental', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(
      await createTrack('alice', { ...SONG, instrumental: true }),
    )

    await testApp.worker.runOnce()
    const track = await json<TrackJson>(await request(`/v1/tracks/${id}`, signedInAs('alice')))

    expect(track.words).toEqual([])
  })

  it('streams the MP3, and a part of it for seeking', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(await createTrack('alice', SONG))
    await testApp.worker.runOnce()

    const whole = await request(`/v1/tracks/${id}/audio`, signedInAs('alice'))
    const part = await request(
      `/v1/tracks/${id}/audio`,
      signedInAs('alice', { headers: { Range: 'bytes=0-99' } }),
    )
    const bytes = new Uint8Array(await whole.arrayBuffer())

    expect(whole.status).toBe(200)
    expect(whole.headers.get('Content-Type')).toBe('audio/mpeg')
    expect([bytes[0], bytes[1]]).toEqual([0xff, 0xfb])
    expect(part.status).toBe(206)
    expect(part.headers.get('Content-Range')).toBe(`bytes 0-99/${bytes.length}`)
    expect((await part.arrayBuffer()).byteLength).toBe(100)
  })

  it('has no audio until it is done', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(await createTrack('alice', SONG))

    const response = await request(`/v1/tracks/${id}/audio`, signedInAs('alice'))

    expect(response.status).toBe(409)
    expect(await errorCode(response)).toBe('NOT_READY')
  })

  it('is invisible to other users', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(await createTrack('alice', SONG))

    expect((await request(`/v1/tracks/${id}`, signedInAs('bob'))).status).toBe(404)
  })
})

describe('a failed generation', () => {
  it('explains why and offers the prompt the provider would accept', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(
      await createTrack('alice', { prompt: 'A song like a real star [mock:bad_prompt]' }),
    )

    await testApp.worker.runOnce()
    const track = await json<TrackJson>(await request(`/v1/tracks/${id}`, signedInAs('alice')))

    expect(track.status).toBe('failed')
    expect(track.error).toMatchObject({
      code: 'INVALID_PROMPT',
      promptSuggestion: expect.stringContaining('synth-pop'),
    })
  })

  it('is retried while the provider is rate limited, then reported', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(
      await createTrack('alice', { prompt: 'Busy afternoon jazz [mock:rate_limit]' }),
    )

    await testApp.worker.runOnce()
    const track = await json<TrackJson>(await request(`/v1/tracks/${id}`, signedInAs('alice')))

    expect(track.error?.code).toBe('RATE_LIMITED')
  })
})

describe('the list of tracks', () => {
  it('pages from the newest with a cursor', async () => {
    await onboard('alice')
    for (const word of ['first', 'second', 'third']) {
      await createTrack('alice', { prompt: `The ${word} song of the evening` })
    }

    const page1 = await json<{ tracks: TrackJson[]; nextCursor: string | null }>(
      await request('/v1/tracks?limit=2', signedInAs('alice')),
    )
    const page2 = await json<{ tracks: TrackJson[]; nextCursor: string | null }>(
      await request(`/v1/tracks?limit=2&cursor=${page1.nextCursor}`, signedInAs('alice')),
    )

    expect(page1.tracks).toHaveLength(2)
    expect(page2.tracks).toHaveLength(1)
    expect(page2.nextCursor).toBeNull()
    expect(new Set([...page1.tracks, ...page2.tracks].map(({ id }) => id)).size).toBe(3)
  })

  it('rejects a cursor it did not issue', async () => {
    const response = await request('/v1/tracks?cursor=not-a-cursor', signedInAs('alice'))

    expect(response.status).toBe(400)
  })
})

describe('the daily limit', () => {
  it('stops new generations once spent, and says when it resets', async () => {
    await onboard('alice')
    for (let i = 0; i < 3; i++) await createTrack('alice', SONG)

    const response = await createTrack('alice', SONG)

    expect(response.status).toBe(429)
    expect(await json<ErrorBody>(response)).toMatchObject({
      error: { code: 'DAILY_LIMIT', details: { limit: 3, resetsAt: expect.any(String) } },
    })
  })

  it('does not count failed generations', async () => {
    await onboard('alice')
    await createTrack('alice', { prompt: 'Broken on purpose [mock:credits]' })
    await testApp.worker.runOnce()

    const quota = await json<{ used: number; remaining: number }>(
      await request('/v1/me/quota', signedInAs('alice')),
    )

    expect(quota).toMatchObject({ used: 0, remaining: 3 })
  })

  it('still answers a retry of a request that got through', async () => {
    await onboard('alice')
    await createTrack('alice', SONG, 'last-one')
    await createTrack('alice', SONG)
    await createTrack('alice', SONG)

    expect((await createTrack('alice', SONG, 'last-one')).status).toBe(200)
  })
})

describe('deleting', () => {
  it('a track removes it and its audio', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(await createTrack('alice', SONG))
    await testApp.worker.runOnce()

    const response = await request(`/v1/tracks/${id}`, signedInAs('alice', { method: 'DELETE' }))

    expect(response.status).toBe(204)
    expect((await request(`/v1/tracks/${id}`, signedInAs('alice'))).status).toBe(404)
    expect(await readdir(testApp.audioDir)).not.toContain(`${id}.mp3`)
  })

  it('the account removes every track and audio file', async () => {
    await onboard('alice')
    const { id } = await json<TrackJson>(await createTrack('alice', SONG))
    await testApp.worker.runOnce()

    await request('/v1/me', signedInAs('alice', { method: 'DELETE' }))

    expect(await readdir(testApp.audioDir)).not.toContain(`${id}.mp3`)
    const list = await json<{ tracks: TrackJson[] }>(
      await request('/v1/tracks', signedInAs('alice')),
    )
    expect(list.tracks).toEqual([])
  })
})
