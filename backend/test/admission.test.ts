import { Hono } from 'hono'
import { describe, expect, it } from 'vitest'
import { admission } from '../src/core/http/admission.js'
import type { AppEnv } from '../src/core/http/app-env.js'
import { ApiError, errorHandler } from '../src/core/http/errors.js'

const defaults = {
  requestsPerMinute: 20,
  ownerRequestsPerMinute: 10,
  concurrentUploads: 2,
  ownerConcurrentUploads: 1,
}
const deferred = () => {
  let resolve!: () => void
  const promise = new Promise<void>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
describe('HTTP admission', () => {
  it('bounds pre-auth traffic without trusting forwarded identity and resets its window', async () => {
    let time = 1000
    let authCalls = 0
    const limits = admission({ ...defaults, requestsPerMinute: 2 }, () => time)
    const app = new Hono<AppEnv>()
      .use(limits.global)
      .use(async (_, next) => {
        authCalls++
        await next()
      })
      .get('/v1/me', (c) => c.text('ok'))
      .onError(errorHandler)
    for (const ip of ['one', 'two'])
      expect((await app.request('/v1/me', { headers: { 'X-Forwarded-For': ip } })).status).toBe(200)
    const rejected = await app.request('/v1/me', { headers: { 'X-Forwarded-For': 'three' } })
    expect(rejected.status).toBe(429)
    expect(rejected.headers.get('Retry-After')).toBe('60')
    expect(authCalls).toBe(2)
    time += 60000
    expect((await app.request('/v1/me')).status).toBe(200)
  })
  it('isolates authenticated owner budgets and resets elapsed windows', async () => {
    let time = 0
    const limits = admission({ ...defaults, ownerRequestsPerMinute: 1 }, () => time)
    const app = new Hono<AppEnv>()
      .use(async (c, next) => {
        c.set('user', {
          id: c.req.header('Owner') ?? 'alice',
          firebaseUid: 'test',
          onboarded: true,
        })
        await next()
      })
      .use(limits.owner)
      .get('/v1/me', (c) => c.text('ok'))
      .onError(errorHandler)
    expect((await app.request('/v1/me')).status).toBe(200)
    expect((await app.request('/v1/me')).status).toBe(429)
    expect((await app.request('/v1/me', { headers: { Owner: 'bob' } })).status).toBe(200)
    time = 60000
    expect((await app.request('/v1/me')).status).toBe(200)
  })
  it('rejects excess uploads before reading bytes and releases slots after route failure', async () => {
    const limits = admission(defaults)
    const entered = deferred()
    const finish = deferred()
    let reads = 0
    const app = new Hono<AppEnv>()
      .use(async (c, next) => {
        c.set('user', {
          id: c.req.header('Owner') ?? 'alice',
          firebaseUid: 'test',
          onboarded: true,
        })
        await next()
      })
      .use(limits.owner)
      .post('/v1/uploads', async (c) => {
        await c.req.arrayBuffer()
        reads++
        entered.resolve()
        await finish.promise
        throw new ApiError(422, 'INVALID_RECORDING', 'invalid')
      })
      .onError(errorHandler)
    const first = app.request('/v1/uploads', { method: 'POST', body: 'first' })
    await entered.promise
    const request = new Request('http://localhost/v1/uploads', { method: 'POST', body: 'unread' })
    const rejected = await app.request(request)
    expect(rejected.status).toBe(429)
    expect(request.bodyUsed).toBe(false)
    expect(reads).toBe(1)
    finish.resolve()
    expect((await first).status).toBe(422)
    expect((await app.request('/v1/uploads', { method: 'POST', body: 'retry' })).status).toBe(422)
    expect(reads).toBe(2)
  })
  it('bounds all owners combined while ordinary API requests continue during uploads', async () => {
    const limits = admission({ ...defaults, concurrentUploads: 1 })
    const entered = deferred()
    const finish = deferred()
    const app = new Hono<AppEnv>()
      .use(async (c, next) => {
        c.set('user', {
          id: c.req.header('Owner') ?? 'alice',
          firebaseUid: 'test',
          onboarded: true,
        })
        await next()
      })
      .use(limits.owner)
      .put('/v1/recordings/:id', async (c) => {
        entered.resolve()
        await finish.promise
        return c.text('ok')
      })
      .get('/v1/me', (c) => c.text('ok'))
      .onError(errorHandler)
    const first = app.request('/v1/recordings/first', { method: 'PUT' })
    await entered.promise
    expect(
      (await app.request('/v1/recordings/second', { method: 'PUT', headers: { Owner: 'bob' } }))
        .status,
    ).toBe(429)
    expect((await app.request('/v1/me', { headers: { Owner: 'bob' } })).status).toBe(200)
    finish.resolve()
    expect((await first).status).toBe(200)
    expect(
      (await app.request('/v1/recordings/second', { method: 'PUT', headers: { Owner: 'bob' } }))
        .status,
    ).toBe(200)
  })
})

it('keeps upload concurrency reserved across rate-window expiry', async () => {
  let time = 0
  const limits = admission({ ...defaults, ownerRequestsPerMinute: 1 }, () => time)
  const entered = deferred()
  const finish = deferred()
  const app = new Hono<AppEnv>()
    .use(async (c, next) => {
      c.set('user', { id: 'alice', firebaseUid: 'test', onboarded: true })
      await next()
    })
    .use(limits.owner)
    .post('/v1/uploads', async (c) => {
      entered.resolve()
      await finish.promise
      return c.text('ok')
    })
    .onError(errorHandler)
  const first = app.request('/v1/uploads', { method: 'POST' })
  await entered.promise
  time = 60000
  expect((await app.request('/v1/uploads', { method: 'POST' })).status).toBe(429)
  finish.resolve()
  expect((await first).status).toBe(200)
  time = 120000
  expect((await app.request('/v1/uploads', { method: 'POST' })).status).toBe(200)
})
