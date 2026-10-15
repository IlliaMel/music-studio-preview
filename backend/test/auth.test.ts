import { Hono } from 'hono'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { InvalidTokenError } from '../src/core/auth/auth-provider.js'
import { requireOnboarding } from '../src/core/auth/authenticate.js'
import { DevAuthProvider } from '../src/core/auth/dev-auth-provider.js'
import type { DatabaseHandle } from '../src/core/db/database.js'
import type { AppEnv, CurrentUser, Feature } from '../src/core/http/app-env.js'
import type { ErrorBody } from '../src/core/http/errors.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

const guarded: Feature = {
  name: 'guarded',
  routes: new Hono<AppEnv>()
    .get('/guarded/whoami', (c) => c.json(c.get('user')))
    .post('/guarded/create', requireOnboarding, (c) => c.json({ created: true }, 201)),
}

let database: DatabaseHandle
let app: Awaited<ReturnType<typeof createTestApp>>['app']

beforeAll(async () => {
  database = await createTestDatabase()
  app = (await createTestApp(database.db, { features: [guarded] })).app
})

beforeEach(() => truncateAll(database.db))

afterAll(() => database.close())

const errorCode = async (response: Response) => ((await response.json()) as ErrorBody).error.code

describe('authenticate', () => {
  it('asks for a bearer token when there is none', async () => {
    const response = await app.request('/v1/guarded/whoami')

    expect(response.status).toBe(401)
    expect(response.headers.get('WWW-Authenticate')).toBe('Bearer')
    expect(await errorCode(response)).toBe('UNAUTHORIZED')
  })

  it.each([
    ['another scheme', 'Basic YWxpY2U6c2VjcmV0'],
    ['no token', 'Bearer'],
    ['extra words', 'Bearer dev:alice extra'],
    ['a token the provider rejects', 'Bearer dev:Not-Valid!'],
  ])('rejects %s', async (_, header) => {
    const response = await app.request('/v1/guarded/whoami', { headers: { Authorization: header } })

    expect(response.status).toBe(401)
    expect(await errorCode(response)).toBe('UNAUTHORIZED')
  })

  it('creates the user on the first request and finds the same one afterwards', async () => {
    const first = (await (
      await app.request('/v1/guarded/whoami', signedInAs('alice'))
    ).json()) as CurrentUser
    const second = (await (
      await app.request('/v1/guarded/whoami', signedInAs('alice'))
    ).json()) as CurrentUser

    expect(first).toEqual({
      id: expect.any(String),
      firebaseUid: 'dev-alice',
      onboarded: false,
      deleting: false,
    })
    expect(second.id).toBe(first.id)
  })

  it('reports a provider outage as a server error, not as "sign in again"', async () => {
    class UnreachableFirebase extends DevAuthProvider {
      override async verifyIdToken(): Promise<never> {
        throw new Error('getaddrinfo ENOTFOUND www.googleapis.com')
      }
    }
    const outage = (await createTestApp(database.db, { auth: new UnreachableFirebase() })).app

    const response = await outage.request('/v1/me', signedInAs('alice'))

    expect(response.status).toBe(500)
    expect(await errorCode(response)).toBe('INTERNAL')
  })

  it('treats an expired token as unauthorized', async () => {
    class ExpiredTokens extends DevAuthProvider {
      override async verifyIdToken(): Promise<never> {
        throw new InvalidTokenError('auth/id-token-expired')
      }
    }
    const expired = (await createTestApp(database.db, { auth: new ExpiredTokens() })).app

    const response = await expired.request('/v1/me', signedInAs('alice'))

    expect(response.status).toBe(401)
  })
})

describe('requireOnboarding', () => {
  it('blocks a user who has not finished onboarding', async () => {
    const response = await app.request(
      '/v1/guarded/create',
      signedInAs('alice', { method: 'POST' }),
    )

    expect(response.status).toBe(403)
    expect(await errorCode(response)).toBe('ONBOARDING_REQUIRED')
  })

  it('lets the user through once the profile is complete', async () => {
    await app.request(
      '/v1/me',
      signedInAs('alice', {
        method: 'PATCH',
        body: { displayName: 'Alice', ageConfirmed: true, acceptedTermsVersion: TERMS_VERSION },
      }),
    )

    const response = await app.request(
      '/v1/guarded/create',
      signedInAs('alice', { method: 'POST' }),
    )

    expect(response.status).toBe(201)
  })
})
