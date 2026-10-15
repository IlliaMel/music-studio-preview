import { eq } from 'drizzle-orm'
import { afterAll, beforeAll, beforeEach, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { uploads } from '../src/features/library/library.table.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

let database: DatabaseHandle
beforeAll(async () => {
  database = await createTestDatabase()
})
beforeEach(() => truncateAll(database.db))
afterAll(() => database.close())
const upload = (app: Awaited<ReturnType<typeof createTestApp>>['app'], user = 'alice') =>
  app.request('/v1/uploads', {
    method: 'POST',
    headers: { Authorization: `Bearer dev:${user}`, 'Content-Type': 'audio/mp4' },
    body: new Uint8Array([1, 2, 3]),
  })
it('enforces count and byte budgets and isolates ownership-safe deletion', async () => {
  const { app } = await createTestApp(database.db)
  let id = ''
  for (let index = 0; index < 20; index++) {
    const response = await upload(app)
    expect(response.status).toBe(201)
    id = ((await response.json()) as { id: string }).id
  }
  expect((await upload(app)).status).toBe(429)
  expect(
    (await app.request(`/v1/uploads/${id}`, signedInAs('bob', { method: 'DELETE' }))).status,
  ).toBe(404)
  expect(
    (await app.request(`/v1/uploads/${id}`, signedInAs('alice', { method: 'DELETE' }))).status,
  ).toBe(204)
  await database.db
    .update(uploads)
    .set({ sizeBytes: 100 * 1024 * 1024 })
    .where(eq(uploads.id, (await database.db.select().from(uploads))[0]?.id ?? ''))
  expect((await upload(app)).status).toBe(429)
})
it('expires unused uploads while retaining an active generation reference until it finishes', async () => {
  const { app, worker, purgeExpiredUploads } = await createTestApp(database.db)
  await app.request(
    '/v1/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
  const first = (await (await upload(app)).json()) as { id: string }
  const second = (await (await upload(app)).json()) as { id: string }
  const create = await app.request(
    '/v1/tracks',
    signedInAs('alice', {
      method: 'POST',
      body: { prompt: 'A song conditioned on my recording', referenceUploadId: first.id },
      headers: { 'Idempotency-Key': 'active-reference-retention' },
    }),
  )
  expect(create.status).toBe(202)
  expect(
    (await app.request(`/v1/uploads/${first.id}`, signedInAs('alice', { method: 'DELETE' })))
      .status,
  ).toBe(409)
  await database.db.update(uploads).set({ createdAt: new Date(0) })
  await purgeExpiredUploads()
  expect((await database.db.select().from(uploads)).some((row) => row.id === second.id)).toBe(false)
  expect((await database.db.select().from(uploads)).map((row) => row.id)).toEqual([first.id])
  await worker.runOnce()
  await purgeExpiredUploads()
  expect(await database.db.select().from(uploads)).toEqual([])
})
