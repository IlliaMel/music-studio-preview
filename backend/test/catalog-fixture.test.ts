import { afterAll, beforeAll, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { catalogMetadata, catalogTracks, collections } from '../src/features/library/catalog.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase } from './support/database.js'

let database: DatabaseHandle
let app: Awaited<ReturnType<typeof createTestApp>>['app']
beforeAll(async () => {
  database = await createTestDatabase()
  app = (await createTestApp(database.db)).app
})
afterAll(() => database.close())

it('keeps a versioned fictional Top20 with twenty distinct ranked entries', () => {
  expect(catalogMetadata).toMatchObject({
    schemaVersion: 1,
    catalogVersion: '2026-10-16.fixture.1',
    provenance: { provider: 'fixture', fictional: true },
  })
  const chart = collections.find((collection) => collection.id === 'top-0')
  const snapshot = catalogMetadata.trendSnapshots.find((item) => item.id === chart?.trendSnapshotId)
  expect(chart?.trackIds).toHaveLength(20)
  expect(new Set(chart?.trackIds).size).toBe(20)
  expect(snapshot?.entries.map((entry) => entry.trackId)).toEqual(chart?.trackIds)
  expect(snapshot?.entries.map((entry) => entry.rank)).toEqual(
    Array.from({ length: 20 }, (_, i) => i + 1),
  )
  expect(snapshot?.window).toEqual({ start: '2026-10-08T00:00:00Z', end: '2026-10-15T00:00:00Z' })
  expect(snapshot?.entries.every((entry) => entry.playCount >= 0)).toBe(true)
  expect(snapshot?.provider).toBe('fixture')
})

it('serves every canonical catalog audio ID including newly added tracks without full WAV allocation', async () => {
  for (const track of catalogTracks) {
    const response = await app.request('/v1/catalog/tracks/' + track.id + '/audio', {
      ...signedInAs('catalog-audio'),
      headers: { Authorization: 'Bearer dev:catalog-audio', Range: 'bytes=0-43' },
    })
    expect(response.status, track.id).toBe(206)
    expect(response.headers.get('content-type')).toBe('audio/wav')
    const bytes = Buffer.from(await response.arrayBuffer())
    expect(bytes.length).toBe(44)
    expect(bytes.subarray(0, 4).toString()).toBe('RIFF')
  }
})

it('preserves chart ordering in collection detail and keeps private likes out of trend snapshots', async () => {
  const fetch = async (path: string) => {
    const response = await app.request('/v1' + path, signedInAs('catalog-user'))
    return response.json() as Promise<Record<string, unknown>>
  }
  const before = await fetch('/trends')
  await app.request('/v1/likes/wildflower', signedInAs('catalog-user', { method: 'PUT' }))
  expect(await fetch('/trends')).toEqual(before)
  const detail = await fetch('/collections/top-0')
  const tracks = detail.tracks as { id: string }[]
  expect(tracks.map((track) => track.id)).toEqual(
    collections.find((item) => item.id === 'top-0')?.trackIds,
  )
  const catalog = await fetch('/catalog')
  expect(catalog).not.toHaveProperty('likes')
  expect(JSON.stringify(catalog)).not.toContain('isLiked')
  expect(catalog.trendSnapshots).toEqual(before.trendSnapshots)
})
