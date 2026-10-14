import { Hono } from 'hono'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { z } from 'zod'
import type { DatabaseHandle } from '../src/core/db/database.js'
import type { AppEnv, Feature } from '../src/core/http/app-env.js'
import { ApiError, type ErrorBody } from '../src/core/http/errors.js'
import { validate } from '../src/core/http/validation.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase } from './support/database.js'

// A feature that exists only to exercise the shared HTTP plumbing.
const probeFeature: Feature = {
  name: 'probe',
  routes: new Hono<AppEnv>()
    .post('/probe/echo', validate('json', z.object({ name: z.string().min(2) })), (c) =>
      c.json({ hello: c.req.valid('json').name }),
    )
    .get('/probe/conflict', () => {
      throw new ApiError(409, 'ALREADY_RUNNING', 'The job has already started')
    })
    .get('/probe/crash', () => {
      throw new Error('database password is hunter2')
    }),
}

let database: DatabaseHandle
let app: ReturnType<typeof createTestApp>['app']

beforeAll(async () => {
  database = await createTestDatabase()
  app = createTestApp(database.db, { features: [probeFeature] }).app
})

afterAll(() => database.close())

describe('health', () => {
  it('is ok when the database answers, without signing in', async () => {
    const response = await app.request('/health')

    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ ok: true })
  })

  it('reports 503 when the database is gone', async () => {
    const broken = await createTestDatabase()
    await broken.close()

    const response = await createTestApp(broken.db).app.request('/health')

    expect(response.status).toBe(503)
    expect(await response.json()).toEqual({ ok: false })
  })
})

describe('request id', () => {
  it('is generated for every response', async () => {
    const response = await app.request('/health')

    expect(response.headers.get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('is kept when the client sends one, so both logs share it', async () => {
    const response = await app.request('/health', { headers: { 'X-Request-Id': 'app-1234' } })

    expect(response.headers.get('X-Request-Id')).toBe('app-1234')
  })
})

describe('feature routes', () => {
  it('are served under /v1', async () => {
    const response = await app.request(
      '/v1/probe/echo',
      signedInAs('alice', { method: 'POST', body: { name: 'Illia' } }),
    )

    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ hello: 'Illia' })
  })

  it('reject an invalid body with every problem listed', async () => {
    const response = await app.request(
      '/v1/probe/echo',
      signedInAs('alice', { method: 'POST', body: { name: 'I' } }),
    )

    expect(response.status).toBe(400)
    expect(await response.json()).toEqual({
      error: {
        code: 'VALIDATION',
        message: 'The request is not valid',
        details: [{ path: 'name', message: expect.any(String) }],
      },
    })
  })

  it('reject a body larger than 64 KB before reading it all', async () => {
    const response = await app.request(
      '/v1/probe/echo',
      signedInAs('alice', { method: 'POST', body: { name: 'x'.repeat(70_000) } }),
    )

    expect(response.status).toBe(413)
    expect(((await response.json()) as ErrorBody).error.code).toBe('PAYLOAD_TOO_LARGE')
  })

  it('reject an oversized body sent in chunks, without a Content-Length', async () => {
    const chunk = new TextEncoder().encode('x'.repeat(16 * 1024))
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        for (let i = 0; i < 5; i++) controller.enqueue(chunk)
        controller.close()
      },
    })

    const response = await app.request('/v1/probe/echo', {
      method: 'POST',
      body,
      headers: { 'Content-Type': 'application/json' },
      duplex: 'half',
    } as RequestInit)

    expect(response.status).toBe(413)
    expect(((await response.json()) as ErrorBody).error.code).toBe('PAYLOAD_TOO_LARGE')
  })
})

describe('errors', () => {
  it('keep the status and code of an ApiError', async () => {
    const response = await app.request('/v1/probe/conflict', signedInAs('alice'))

    expect(response.status).toBe(409)
    expect(await response.json()).toEqual({
      error: { code: 'ALREADY_RUNNING', message: 'The job has already started' },
    })
  })

  it('hide the details of an unexpected failure', async () => {
    const response = await app.request('/v1/probe/crash', signedInAs('alice'))
    const text = await response.text()

    expect(response.status).toBe(500)
    expect((JSON.parse(text) as ErrorBody).error.code).toBe('INTERNAL')
    expect(text).not.toContain('hunter2')
  })

  it('answer an unknown route with 404 in the same format', async () => {
    const response = await app.request('/v1/nothing-here', signedInAs('alice'))

    expect(response.status).toBe(404)
    expect(await response.json()).toEqual({
      error: { code: 'NOT_FOUND', message: 'No route for GET /v1/nothing-here' },
    })
  })
})
