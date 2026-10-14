import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import type { ErrorBody } from '../src/core/http/errors.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, type RecordingAuthProvider, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

type Profile = {
  id: string
  email: string | null
  displayName: string | null
  ageConfirmed: boolean
  termsVersion: string | null
  marketingOptIn: boolean
  onboarding: { complete: boolean; missing: string[]; termsVersion: string }
}

let database: DatabaseHandle
let app: ReturnType<typeof createTestApp>['app']
let auth: RecordingAuthProvider

beforeAll(async () => {
  database = await createTestDatabase()
  const testApp = createTestApp(database.db)
  app = testApp.app
  auth = testApp.auth as RecordingAuthProvider
})

beforeEach(async () => {
  await truncateAll(database.db)
  auth.deletedAccounts.length = 0
})

afterAll(() => database.close())

const getProfile = async (name: string) =>
  (await (await app.request('/v1/me', signedInAs(name))).json()) as Profile

const patchProfile = (name: string, body: unknown) =>
  app.request('/v1/me', signedInAs(name, { method: 'PATCH', body }))

describe('GET /v1/me', () => {
  it('returns a new profile with every onboarding step still to do', async () => {
    const response = await app.request('/v1/me', signedInAs('alice'))

    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({
      id: expect.any(String),
      email: 'alice@dev.local',
      emailVerified: true,
      signInProvider: 'dev',
      displayName: null,
      ageConfirmed: false,
      termsVersion: null,
      marketingOptIn: false,
      onboarding: {
        complete: false,
        missing: ['displayName', 'age', 'terms'],
        termsVersion: TERMS_VERSION,
      },
      createdAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T/),
    })
  })

  it('keeps each user to their own profile', async () => {
    await patchProfile('alice', { displayName: 'Alice' })

    expect((await getProfile('bob')).displayName).toBeNull()
  })
})

describe('PATCH /v1/me', () => {
  it('saves the steps one at a time, as the onboarding screens send them', async () => {
    await patchProfile('alice', { displayName: '  Alice  ' })
    const afterName = await getProfile('alice')
    await patchProfile('alice', { ageConfirmed: true, marketingOptIn: true })
    const afterAge = await getProfile('alice')

    expect(afterName.displayName).toBe('Alice')
    expect(afterName.onboarding.missing).toEqual(['age', 'terms'])
    expect(afterAge).toMatchObject({ ageConfirmed: true, marketingOptIn: true })
    expect(afterAge.onboarding.missing).toEqual(['terms'])
  })

  it('completes onboarding once the current terms are accepted', async () => {
    const response = await patchProfile('alice', {
      displayName: 'Alice',
      ageConfirmed: true,
      acceptedTermsVersion: TERMS_VERSION,
    })

    expect(response.status).toBe(200)
    expect(((await response.json()) as Profile).onboarding).toEqual({
      complete: true,
      missing: [],
      termsVersion: TERMS_VERSION,
    })
  })

  it('refuses outdated terms and says which version is current', async () => {
    const response = await patchProfile('alice', { acceptedTermsVersion: '2020-01-01' })

    expect(response.status).toBe(409)
    expect(await response.json()).toEqual({
      error: {
        code: 'TERMS_OUTDATED',
        message: expect.any(String),
        details: { termsVersion: TERMS_VERSION },
      },
    })
  })

  it.each([
    ['an empty body', {}],
    ['a blank name', { displayName: '   ' }],
    ['a name over 40 characters', { displayName: 'A'.repeat(41) }],
    ['taking the age confirmation back', { ageConfirmed: false }],
    ['a field that does not exist', { isAdmin: true }],
  ])('rejects %s', async (_, body) => {
    const response = await patchProfile('alice', body)

    expect(response.status).toBe(400)
    expect(((await response.json()) as ErrorBody).error.code).toBe('VALIDATION')
  })
})

describe('DELETE /v1/me', () => {
  it('removes our data and the Firebase account', async () => {
    const before = await getProfile('alice')

    const response = await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))

    expect(response.status).toBe(204)
    expect(auth.deletedAccounts).toEqual(['dev-alice'])
    // The old token is still valid for up to an hour; a request with it meets an empty profile.
    const after = await getProfile('alice')
    expect(after.id).not.toBe(before.id)
  })

  it('can be repeated when the first attempt did not finish', async () => {
    await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))
    const retry = await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))

    expect(retry.status).toBe(204)
  })
})
