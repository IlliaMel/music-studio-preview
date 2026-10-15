import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

type TrackJson = {
  audioUrl: string | null
  id: string
  generationId: string
  variantIndex: number
  customLyrics: string | null
  words: { word: string }[]
}
type Body = TrackJson & {
  tracks: [TrackJson, TrackJson, ...TrackJson[]]
  collections: [{ id: string; trackIds: string[] }, ...{ id: string; trackIds: string[] }[]]
  stations: [{ id: string }, ...{ id: string }[]]
  trackIds: string[]
  playlists: unknown[]
  name: string
}
interface TestResponse extends Response {
  json(): Promise<Body>
}
let database: DatabaseHandle
let testApp: Awaited<ReturnType<typeof createTestApp>>
beforeAll(async () => {
  database = await createTestDatabase()
  testApp = await createTestApp(database.db, { dailyLimit: 1 })
})
beforeEach(() => truncateAll(database.db))
afterAll(() => database.close())
const request = (path: string, init = signedInAs('alice')) =>
  Promise.resolve(testApp.app.request(`/v1${path}`, init)).then(
    (response) => response as TestResponse,
  )
const patchTerms = () =>
  request(
    '/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
const song = { prompt: 'A bright electronic melody for morning', variants: 2 }
const create = (key = 'batch-request-1', body: unknown = song) =>
  request(
    '/tracks',
    signedInAs('alice', { method: 'POST', body, headers: { 'Idempotency-Key': key } }),
  )

describe('grouped generation', () => {
  it('requires terms but no age or custom-name onboarding', async () => {
    expect((await create()).status).toBe(403)
    const profile = await (await patchTerms()).json()
    expect(profile).toMatchObject({
      displayName: 'alice',
      ageConfirmed: false,
      onboarding: { complete: true, missing: [] },
    })
    expect((await create()).status).toBe(202)
  })
  it('creates two tracks atomically for one credit and retries the same group', async () => {
    await patchTerms()
    const first = await (await create()).json()
    expect(first.tracks).toHaveLength(2)
    expect(first.tracks[0].generationId).toBe(first.tracks[1].generationId)
    expect(first.tracks.map((track: { variantIndex: number }) => track.variantIndex)).toEqual([
      0, 1,
    ])
    const retry = await create()
    expect(retry.status).toBe(200)
    expect((await retry.json()).tracks.map((track: { id: string }) => track.id)).toEqual(
      first.tracks.map((track: { id: string }) => track.id),
    )
    expect(await (await request('/me/quota')).json()).toMatchObject({ used: 1, remaining: 0 })
    expect((await create('batch-request-2')).status).toBe(429)
  })
  it('serializes two simultaneous groups at the final credit', async () => {
    await patchTerms()
    const responses = await Promise.all([create('concurrent-key-1'), create('concurrent-key-2')])
    expect(responses.map((response) => response.status).sort()).toEqual([202, 429])
    expect((await (await request('/tracks')).json()).tracks).toHaveLength(2)
  })
  it('rejects reusing the idempotency key with changed lyrics or variants', async () => {
    await patchTerms()
    await create()
    expect((await create('batch-request-1', { ...song, variants: 1 })).status).toBe(422)
    expect((await create('batch-request-1', { ...song, quality: 'deep' })).status).toBe(422)
    expect(
      (await create('batch-request-1', { ...song, customLyrics: 'Changed lyrics' })).status,
    ).toBe(422)
  })
  it('passes custom lyrics through to the provider and word timings', async () => {
    await patchTerms()
    const track = await (
      await create('lyrics-request', { ...song, variants: 1, customLyrics: 'Hello Kyiv tonight' })
    ).json()
    await testApp.worker.runOnce()
    const done = await (await request(`/tracks/${track.id}`)).json()
    expect(done.customLyrics).toBe('Hello Kyiv tonight')
    expect(done.words).toHaveLength(3)
    expect(done.words.slice(0, 3).map((word: { word: string }) => word.word)).toEqual([
      'Hello',
      'Kyiv',
      'tonight',
    ])
  })
  it('rejects missing and other-user references', async () => {
    await patchTerms()
    expect(
      (await create('missing-reference', { ...song, remixTrackId: crypto.randomUUID() })).status,
    ).toBe(404)
    const raw = await request('/uploads', {
      method: 'POST',
      headers: { Authorization: 'Bearer dev:bob', 'Content-Type': 'audio/mp4' },
      body: new Uint8Array([0, 1, 2]),
    })
    const upload = await raw.json()
    expect(
      (await create('foreign-reference', { ...song, referenceUploadId: upload.id })).status,
    ).toBe(404)
  })
})

describe('remix and lifecycle', () => {
  it('accepts catalog originals and refuses remix-of-remix', async () => {
    await patchTerms()
    const original = await create('catalog-remix-1', {
      ...song,
      variants: 1,
      remixTrackId: 'glass-harbor',
    })
    expect(original.status).toBe(202)
    const remix = await original.json()
    await testApp.worker.runOnce()
    expect((await create('recursive-remix', { ...song, remixTrackId: remix.id })).status).toBe(409)
  })
  it('refuses an original that is still generating', async () => {
    await patchTerms()
    const queued = await (await create()).json()
    expect((await create('queued-remix-1', { ...song, remixTrackId: queued.id })).status).toBe(409)
  })
  it('removes a deleted track from likes and playlists without refunding the group', async () => {
    await patchTerms()
    const track = await (await create()).json()
    await request(`/likes/${track.id}`, signedInAs('alice', { method: 'PUT' }))
    const playlist = await (
      await request('/playlists', signedInAs('alice', { method: 'POST', body: { name: 'Saved' } }))
    ).json()
    await request(
      `/playlists/${playlist.id}/tracks/${track.id}`,
      signedInAs('alice', { method: 'PUT' }),
    )
    await request(`/tracks/${track.id}`, signedInAs('alice', { method: 'DELETE' }))
    expect((await (await request('/likes')).json()).trackIds).toEqual([])
    expect((await (await request(`/playlists/${playlist.id}`)).json()).trackIds).toEqual([])
    expect(await (await request('/me/quota')).json()).toMatchObject({ used: 1 })
  })
})

describe('library', () => {
  it('lists the mock catalog with collection and station membership', async () => {
    const catalog = await (await request('/catalog')).json()
    expect(catalog.tracks).toHaveLength(12)
    expect(catalog.tracks[0].audioUrl).toBe(
      'https://api.studio.test/v1/catalog/tracks/glass-harbor/audio',
    )
    const collection = await (await request(`/collections/${catalog.collections[0].id}`)).json()
    expect(collection.tracks[0].id).toBe(catalog.collections[0].trackIds[0])
    expect((await request(`/stations/${catalog.stations[0].id}`)).status).toBe(200)
    const rotatedCollection = await (await request('/collections/new-1')).json()
    const expectedCollection = catalog.collections.find((item) => item.id === 'new-1')
    expect(rotatedCollection.tracks.map((track) => track.id)).toEqual(expectedCollection?.trackIds)
    expect(rotatedCollection.tracks[0].id).toBe('paper-moons')
    const rotatedStation = await (await request('/stations/stations-1')).json()
    expect(rotatedStation.tracks.map((track) => track.id)).toEqual([
      'night-drive',
      'paper-moons',
      'cold-brew',
      'signal-fire',
      'copper-sky',
      'lanterns',
      'static-bloom',
      'afterglow',
      'slow-tide',
      'kinetic',
      'low-orbit',
      'glass-harbor',
    ])
  })
  it('persists likes idempotently and isolates users', async () => {
    for (let i = 0; i < 2; i++)
      expect(
        (await request('/likes/glass-harbor', signedInAs('alice', { method: 'PUT' }))).status,
      ).toBe(204)
    expect(await (await request('/likes')).json()).toEqual({ trackIds: ['glass-harbor'] })
    expect(await (await request('/likes', signedInAs('bob'))).json()).toEqual({ trackIds: [] })
    expect(
      (await request('/likes/not-a-track', signedInAs('alice', { method: 'PUT' }))).status,
    ).toBe(404)
    await request('/likes/glass-harbor', signedInAs('alice', { method: 'DELETE' }))
    expect(await (await request('/likes')).json()).toEqual({ trackIds: [] })
  })
  it('creates, edits and deletes playlists with unique ordered membership', async () => {
    const created = await request(
      '/playlists',
      signedInAs('alice', { method: 'POST', body: { name: 'Evening' } }),
    )
    expect(created.status).toBe(201)
    const playlist = await created.json()
    const path = `/playlists/${playlist.id}`
    for (let i = 0; i < 2; i++)
      expect(
        (await request(`${path}/tracks/glass-harbor`, signedInAs('alice', { method: 'PUT' })))
          .status,
      ).toBe(204)
    expect((await (await request(path)).json()).trackIds).toEqual(['glass-harbor'])
    expect((await request(path, signedInAs('bob'))).status).toBe(404)
    expect(
      (await request(`${path}/tracks/paper-moons`, signedInAs('bob', { method: 'PUT' }))).status,
    ).toBe(404)
    const renamed = await request(
      path,
      signedInAs('alice', { method: 'PATCH', body: { name: 'Renamed' } }),
    )
    expect((await renamed.json()).name).toBe('Renamed')
    await request(`${path}/tracks/glass-harbor`, signedInAs('alice', { method: 'DELETE' }))
    expect((await (await request(path)).json()).trackIds).toEqual([])
    await request(path, signedInAs('alice', { method: 'DELETE' }))
    expect((await (await request('/playlists')).json()).playlists).toEqual([])
  })
  it('rejects blank names and unknown track references', async () => {
    expect(
      (await request('/playlists', signedInAs('alice', { method: 'POST', body: { name: '   ' } })))
        .status,
    ).toBe(400)
    const playlist = await (
      await request('/playlists', signedInAs('alice', { method: 'POST', body: { name: 'Test' } }))
    ).json()
    expect(
      (
        await request(
          `/playlists/${playlist.id}/tracks/missing`,
          signedInAs('alice', { method: 'PUT' }),
        )
      ).status,
    ).toBe(404)
  })
})

describe('reference uploads', () => {
  it('accepts raw audio larger than JSON limits and associates it with a generation', async () => {
    await patchTerms()
    const response = await request('/uploads', {
      method: 'POST',
      headers: { Authorization: 'Bearer dev:alice', 'Content-Type': 'audio/wav' },
      body: new Uint8Array(100000),
    })
    expect(response.status).toBe(201)
    const upload = await response.json()
    expect(upload).toMatchObject({
      contentType: 'audio/wav',
      sizeBytes: 100000,
      provider: 'mock',
      durationMs: null,
    })
    expect(
      (await create('upload-generation', { ...song, referenceUploadId: upload.id })).status,
    ).toBe(202)
  })
  it('rejects unsupported and empty audio', async () => {
    for (const [contentType, body, status] of [
      ['text/plain', 'hello', 415],
      ['audio/mpeg', '', 400],
    ] as const) {
      const response = await request('/uploads', {
        method: 'POST',
        headers: { Authorization: 'Bearer dev:alice', 'Content-Type': contentType },
        body,
      })
      expect(response.status).toBe(status)
    }
  })
  it('removes library and reference data with the account', async () => {
    await request('/likes/glass-harbor', signedInAs('alice', { method: 'PUT' }))
    await request('/playlists', signedInAs('alice', { method: 'POST', body: { name: 'Private' } }))
    await request('/me', signedInAs('alice', { method: 'DELETE' }))
    expect(await (await request('/likes')).json()).toEqual({ trackIds: [] })
    expect(await (await request('/playlists')).json()).toEqual({ playlists: [] })
  })
})
