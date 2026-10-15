import { randomUUID } from 'node:crypto'
import { readdir } from 'node:fs/promises'
import { eq } from 'drizzle-orm'
import { afterAll, beforeAll, beforeEach, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { LocalAudioStorage } from '../src/core/storage/audio-storage.js'
import { recordingKey } from '../src/features/recordings/recordings.service.js'
import { recordings } from '../src/features/recordings/recordings.table.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

let database: DatabaseHandle
beforeAll(async () => {
  database = await createTestDatabase()
})
beforeEach(() => truncateAll(database.db))
afterAll(() => database.close())
type App = Awaited<ReturnType<typeof createTestApp>>['app']
const audio = Buffer.concat([
  Buffer.from([0, 0, 0, 24]),
  Buffer.from('ftypM4A mdat'),
  Buffer.alloc(100),
])
const put = (
  app: App,
  id: string = randomUUID(),
  user = 'alice',
  bytes = audio,
  title = 'Мій запис',
  duration = '3200',
) =>
  app.request(`/v1/recordings/${id}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer dev:${user}`,
      'Content-Type': 'audio/mp4',
      'X-Recording-Title': encodeURIComponent(title),
      'X-Recording-Duration-Ms': duration,
    },
    body: new Uint8Array(bytes),
  })
const json = async (response: Response) => (await response.json()) as Record<string, unknown>

it('persists raw recordings separately from temporary uploads and generation credits, including Range/HEAD', async () => {
  const { app, storage, purgeExpiredUploads } = await createTestApp(database.db)
  const id = randomUUID()
  const bytes = Buffer.concat([audio, Buffer.alloc(80 * 1024)])
  const saved = await put(app, id, 'alice', bytes)
  expect(saved.status).toBe(201)
  const dto = await json(saved)
  expect(dto).toMatchObject({
    id,
    title: 'Мій запис',
    contentType: 'audio/mp4',
    durationMs: 3200,
    sizeBytes: bytes.length,
    audioUrl: `https://api.studio.test/v1/recordings/${id}/audio`,
  })
  expect(Object.keys(dto).sort()).toEqual(
    [
      'audioUrl',
      'contentType',
      'createdAt',
      'durationMs',
      'id',
      'sizeBytes',
      'title',
      'updatedAt',
    ].sort(),
  )
  const [row] = await database.db.select().from(recordings)
  expect(row).toBeDefined()
  expect(await storage.open(recordingKey(row?.userId ?? '', id))).toBeDefined()
  await database.db.update(recordings).set({ createdAt: new Date(0) })
  await purgeExpiredUploads()
  const restarted = await createTestApp(database.db, { storage: () => storage })
  const list = await json(await restarted.app.request('/v1/recordings', signedInAs('alice')))
  expect(list.recordings as unknown[]).toHaveLength(1)
  const path = `/v1/recordings/${id}/audio`
  const range = await restarted.app.request(
    path,
    signedInAs('alice', { headers: { Range: 'bytes=2-10' } }),
  )
  expect(range.status).toBe(206)
  expect(range.headers.get('content-type')).toBe('audio/mp4')
  expect(Buffer.from(await range.arrayBuffer())).toEqual(bytes.subarray(2, 11))
  const head = await restarted.app.request(path, signedInAs('alice', { method: 'HEAD' }))
  expect(head.status).toBe(200)
  expect(head.headers.get('content-length')).toBe(String(bytes.length))
  expect((await head.arrayBuffer()).byteLength).toBe(0)
  expect(await database.db.query.tracks.findMany()).toHaveLength(0)
})

it('makes identical PUT idempotent after response loss and later rename while rejecting changed original identity', async () => {
  const { app } = await createTestApp(database.db)
  const id = randomUUID()
  expect((await put(app, id)).status).toBe(201)
  expect((await put(app, id)).status).toBe(200)
  expect(
    (
      await app.request(
        `/v1/recordings/${id}`,
        signedInAs('alice', { method: 'PATCH', body: { title: 'Renamed' } }),
      )
    ).status,
  ).toBe(200)
  const retry = await put(app, id)
  expect(retry.status).toBe(200)
  expect((await json(retry)).title).toBe('Renamed')
  expect((await put(app, id, 'alice', Buffer.concat([audio, Buffer.from([1])]))).status).toBe(409)
  expect((await put(app, id, 'alice', audio, 'Different')).status).toBe(409)
  expect((await put(app, id, 'alice', audio, 'Мій запис', '3201')).status).toBe(409)
  expect(await database.db.select().from(recordings)).toHaveLength(1)
})

it('isolates owners even when client recording UUIDs coincide', async () => {
  const { app } = await createTestApp(database.db)
  const id = randomUUID()
  expect((await put(app, id)).status).toBe(201)
  for (const request of [
    signedInAs('bob'),
    signedInAs('bob', { method: 'DELETE' }),
    signedInAs('bob', { method: 'PATCH', body: { title: 'Stolen' } }),
  ]) {
    const suffix = request.method === 'GET' ? '/audio' : ''
    expect((await app.request(`/v1/recordings/${id}${suffix}`, request)).status).toBe(404)
  }
  expect((await json(await app.request('/v1/recordings', signedInAs('bob')))).recordings).toEqual(
    [],
  )
  expect((await put(app, id, 'bob', audio, 'Bob recording')).status).toBe(201)
  expect(await database.db.select().from(recordings)).toHaveLength(2)
})

it('tombstones deletion and does not resurrect a recording on delayed PUT or repeated DELETE', async () => {
  const { app, storage } = await createTestApp(database.db)
  const id = randomUUID()
  await put(app, id)
  const [row] = await database.db.select().from(recordings)
  for (let index = 0; index < 2; index++)
    expect(
      (await app.request(`/v1/recordings/${id}`, signedInAs('alice', { method: 'DELETE' }))).status,
    ).toBe(204)
  expect(await storage.open(recordingKey(row?.userId ?? '', id))).toBeUndefined()
  expect((await app.request(`/v1/recordings/${id}/audio`, signedInAs('alice'))).status).toBe(404)
  expect((await put(app, id)).status).toBe(409)
  expect((await json(await app.request('/v1/recordings', signedInAs('alice')))).recordings).toEqual(
    [],
  )
})

it('validates title, duration, id, MIME, empty and oversized body before allocating storage', async () => {
  const { app } = await createTestApp(database.db)
  expect((await put(app, 'bad-id')).status).toBe(400)
  expect((await put(app, randomUUID(), 'alice', audio, '')).status).toBe(400)
  expect((await put(app, randomUUID(), 'alice', audio, 'a'.repeat(81))).status).toBe(400)
  for (const duration of ['0', '-1', '600001', '3.5', 'NaN'])
    expect((await put(app, randomUUID(), 'alice', audio, 'Title', duration)).status).toBe(400)
  expect((await put(app, randomUUID(), 'alice', Buffer.alloc(0))).status).toBe(422)
  expect((await put(app, randomUUID(), 'alice', Buffer.from('not actual mp4 bytes'))).status).toBe(
    422,
  )
  expect((await put(app, randomUUID(), 'alice', Buffer.alloc(20 * 1024 * 1024 + 1))).status).toBe(
    413,
  )
  const headers = {
    Authorization: 'Bearer dev:alice',
    'Content-Type': 'text/plain',
    'X-Recording-Title': 'Title',
    'X-Recording-Duration-Ms': '1000',
  }
  expect(
    (await app.request(`/v1/recordings/${randomUUID()}`, { method: 'PUT', headers, body: audio }))
      .status,
  ).toBe(415)
  expect(
    (
      await app.request(`/v1/recordings/${randomUUID()}`, {
        method: 'PUT',
        headers: { ...headers, 'Content-Type': 'audio/mp4', 'X-Recording-Title': '%' },
        body: audio,
      })
    ).status,
  ).toBe(400)
  expect(await database.db.select().from(recordings)).toHaveLength(0)
})

it('enforces recording count and byte quotas independently of temporary uploads', async () => {
  const { app } = await createTestApp(database.db)
  let id = ''
  for (let index = 0; index < 20; index++) {
    id = randomUUID()
    expect((await put(app, id)).status).toBe(201)
  }
  expect((await put(app)).status).toBe(429)
  expect((await put(app, id)).status).toBe(200)
  await app.request(`/v1/recordings/${id}`, signedInAs('alice', { method: 'DELETE' }))
  const [row] = await database.db.select().from(recordings)
  await database.db
    .update(recordings)
    .set({ sizeBytes: 100 * 1024 * 1024 })
    .where(eq(recordings.id, row?.id ?? ''))
  expect((await put(app)).status).toBe(429)
})

it('retains a recoverable pending row after save failure and completes the same PUT on restart', async () => {
  let fail = true
  class FaultyStorage extends LocalAudioStorage {
    override async save(key: string, bytes: Uint8Array) {
      if (fail) throw new Error('private disk failure')
      await super.save(key, bytes)
    }
  }
  const { app, storage } = await createTestApp(database.db, {
    storage: (dir) => new FaultyStorage(dir),
  })
  const id = randomUUID()
  expect((await put(app, id)).status).toBe(500)
  expect((await database.db.select().from(recordings))[0]?.ready).toBe(false)
  expect((await json(await app.request('/v1/recordings', signedInAs('alice')))).recordings).toEqual(
    [],
  )
  fail = false
  const restarted = await createTestApp(database.db, { storage: () => storage })
  expect((await put(restarted.app, id)).status).toBe(200)
  expect((await database.db.select().from(recordings))[0]?.ready).toBe(true)
})

it('retries failed recording file cleanup without deleting another owner or resurrecting metadata', async () => {
  let fail = true
  class FaultyStorage extends LocalAudioStorage {
    override async delete(key: string) {
      if (fail && key.startsWith('recording-')) throw new Error('disk locked')
      await super.delete(key)
    }
  }
  const { app, audioDir } = await createTestApp(database.db, {
    storage: (dir) => new FaultyStorage(dir),
  })
  const id = randomUUID()
  await put(app, id)
  await put(app, id, 'bob')
  expect(
    (await app.request(`/v1/recordings/${id}`, signedInAs('alice', { method: 'DELETE' }))).status,
  ).toBe(500)
  expect((await json(await app.request('/v1/recordings', signedInAs('alice')))).recordings).toEqual(
    [],
  )
  fail = false
  expect(
    (await app.request(`/v1/recordings/${id}`, signedInAs('alice', { method: 'DELETE' }))).status,
  ).toBe(204)
  expect(await readdir(audioDir)).toHaveLength(1)
})

it('removes recording files and rows through account deletion including pending and tombstoned records', async () => {
  const { app, audioDir } = await createTestApp(database.db)
  const id = randomUUID()
  await put(app, id)
  const pendingId = randomUUID()
  await put(app, pendingId)
  await database.db.update(recordings).set({ ready: false }).where(eq(recordings.id, pendingId))
  await database.db.update(recordings).set({ deletedAt: new Date() }).where(eq(recordings.id, id))
  await put(app, id, 'bob')
  expect((await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))).status).toBe(204)
  expect(await database.db.select().from(recordings)).toHaveLength(1)
  expect(await readdir(audioDir)).toHaveLength(1)
  expect((await app.request(`/v1/recordings/${id}/audio`, signedInAs('bob'))).status).toBe(200)
})

it('keeps failed deletion bytes charged against recording storage until cleanup succeeds', async () => {
  let fail = true
  class FaultyStorage extends LocalAudioStorage {
    override async delete(key: string) {
      if (fail) throw new Error('disk locked')
      await super.delete(key)
    }
  }
  const { app } = await createTestApp(database.db, { storage: (dir) => new FaultyStorage(dir) })
  const id = randomUUID()
  await put(app, id)
  await database.db.update(recordings).set({ sizeBytes: 100 * 1024 * 1024 })
  expect(
    (await app.request(`/v1/recordings/${id}`, signedInAs('alice', { method: 'DELETE' }))).status,
  ).toBe(500)
  expect((await put(app)).status).toBe(429)
  fail = false
  expect(
    (await app.request(`/v1/recordings/${id}`, signedInAs('alice', { method: 'DELETE' }))).status,
  ).toBe(204)
  expect((await put(app)).status).toBe(201)
})

it('recovers pending recording cleanup after account deletion fails without any client token', async () => {
  let fail = true
  class FaultyStorage extends LocalAudioStorage {
    override async delete(key: string) {
      if (fail && key.startsWith('recording-')) throw new Error('disk locked')
      await super.delete(key)
    }
  }
  const { app, accountDeletionWorker, audioDir } = await createTestApp(database.db, {
    storage: (dir) => new FaultyStorage(dir),
  })
  await put(app)
  expect((await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))).status).toBe(500)
  expect((await put(app)).status).toBe(409)
  expect(await database.db.select().from(recordings)).toHaveLength(1)
  fail = false
  expect(await accountDeletionWorker.runOnce()).toBe(1)
  expect(await database.db.select().from(recordings)).toHaveLength(0)
  expect(await readdir(audioDir)).toHaveLength(0)
})

it('serializes duplicate uploads and delete racing completion without orphaning or resurrecting audio', async () => {
  let started: () => void = () => {}
  let release: () => void = () => {}
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  const saving = new Promise<void>((resolve) => {
    started = resolve
  })
  class SlowStorage extends LocalAudioStorage {
    override async save(key: string, bytes: Uint8Array) {
      started()
      await gate
      await super.save(key, bytes)
    }
  }
  const { app, audioDir } = await createTestApp(database.db, {
    storage: (dir) => new SlowStorage(dir),
  })
  const id = randomUUID()
  const first = put(app, id)
  await saving
  const duplicate = put(app, id)
  const remove = app.request(`/v1/recordings/${id}`, signedInAs('alice', { method: 'DELETE' }))
  release()
  const responses = await Promise.all([first, duplicate, remove])
  expect(responses[0]?.status).toBe(201)
  expect([200, 409]).toContain(responses[1]?.status)
  expect(responses[2]?.status).toBe(204)
  expect(await readdir(audioDir)).toHaveLength(0)
  expect((await put(app, id)).status).toBe(409)
})

it('reclaims only abandoned pending reservations on list, preserving READY recordings, active pending and owner isolation', async () => {
  const { app, storage } = await createTestApp(database.db)
  const stale = randomUUID(),
    ready = randomUUID(),
    active = randomUUID(),
    foreign = randomUUID()
  for (const id of [stale, ready, active]) await put(app, id)
  await put(app, foreign, 'bob')
  await database.db.update(recordings).set({ createdAt: new Date(0), updatedAt: new Date(0) })
  await database.db.update(recordings).set({ ready: false }).where(eq(recordings.id, stale))
  await database.db
    .update(recordings)
    .set({ ready: false, updatedAt: new Date() })
    .where(eq(recordings.id, active))
  await database.db.update(recordings).set({ ready: false }).where(eq(recordings.id, foreign))
  const staleRow = (await database.db.select().from(recordings)).find((row) => row.id === stale)
  const list = await app.request('/v1/recordings', signedInAs('alice'))
  expect(list.status).toBe(200)
  expect((await json(list)).recordings).toHaveLength(1)
  const remaining = await database.db.select().from(recordings)
  expect(remaining.map((row) => row.id).sort()).toEqual([ready, active, foreign].sort())
  expect(await storage.open(recordingKey(staleRow?.userId ?? '', stale))).toBeUndefined()
  expect((await app.request(`/v1/recordings/${ready}/audio`, signedInAs('alice'))).status).toBe(200)
  expect((await put(app, stale)).status).toBe(201)
  expect((await put(app, active)).status).toBe(200)
})

it('reclaims stalled pending budget before PUT and allows the original UUID to resume while keeping deletion tombstones', async () => {
  const { app } = await createTestApp(database.db)
  const id = randomUUID(),
    deleted = randomUUID()
  await put(app, deleted)
  await app.request(`/v1/recordings/${deleted}`, signedInAs('alice', { method: 'DELETE' }))
  await put(app, id)
  await database.db.update(recordings).set({ updatedAt: new Date(0) })
  await database.db
    .update(recordings)
    .set({ ready: false, sizeBytes: 100 * 1024 * 1024 })
    .where(eq(recordings.id, id))
  expect((await put(app, id)).status).toBe(201)
  expect(
    (await database.db.select().from(recordings)).find((row) => row.id === id)?.sizeBytes,
  ).toBe(audio.length)
  expect((await put(app, deleted)).status).toBe(409)
  expect(
    (await database.db.select().from(recordings)).find((row) => row.id === deleted)?.deletedAt,
  ).not.toBeNull()
})

it('does not free expired pending quota until audio cleanup succeeds and retries the same lease safely', async () => {
  let fail = true
  class FaultyStorage extends LocalAudioStorage {
    override async delete(key: string) {
      if (fail) throw new Error('disk cleanup unavailable')
      await super.delete(key)
    }
  }
  const { app, audioDir } = await createTestApp(database.db, {
    storage: (dir) => new FaultyStorage(dir),
  })
  const id = randomUUID()
  await put(app, id)
  await database.db
    .update(recordings)
    .set({ ready: false, sizeBytes: 100 * 1024 * 1024, updatedAt: new Date(0) })
  expect((await app.request('/v1/recordings', signedInAs('alice'))).status).toBe(500)
  expect((await put(app)).status).toBe(500)
  expect((await database.db.select().from(recordings))[0]?.sizeBytes).toBe(100 * 1024 * 1024)
  expect(await readdir(audioDir)).toHaveLength(1)
  fail = false
  expect((await put(app, id)).status).toBe(201)
  expect(await database.db.select().from(recordings)).toHaveLength(1)
  expect(await readdir(audioDir)).toHaveLength(1)
})
