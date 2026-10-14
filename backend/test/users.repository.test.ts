import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { type SignInIdentity, UsersRepository } from '../src/features/users/users.repository.js'
import { createTestDatabase, truncateAll } from './support/database.js'

let database: DatabaseHandle
let repository: UsersRepository

beforeAll(async () => {
  database = await createTestDatabase()
  repository = new UsersRepository(database.db)
})

beforeEach(() => truncateAll(database.db))

afterAll(() => database.close())

const now = new Date('2026-10-14T12:00:00Z')

const alice: SignInIdentity = {
  firebaseUid: 'firebase-alice',
  email: 'alice@example.com',
  emailVerified: true,
  signInProvider: 'google.com',
}

describe('UsersRepository', () => {
  it('creates a user with an empty profile on first sign-in', async () => {
    const user = await repository.insertOrGet(alice, now)

    expect(user).toMatchObject({
      ...alice,
      id: expect.stringMatching(/^[0-9a-f-]{36}$/),
      displayName: null,
      ageConfirmedAt: null,
      termsVersion: null,
      marketingOptIn: false,
    })
    expect(user.createdAt).toEqual(now)
    expect(user.lastSeenAt).toEqual(now)
  })

  it('returns the existing user instead of creating a second one', async () => {
    const first = await repository.insertOrGet(alice, now)
    const second = await repository.insertOrGet({ ...alice, email: 'changed@example.com' }, now)

    expect(second.id).toBe(first.id)
    expect(second.email).toBe('alice@example.com')
  })

  it('creates one user when the first requests arrive at the same time', async () => {
    const results = await Promise.all([1, 2, 3].map(() => repository.insertOrGet(alice, now)))

    expect(new Set(results.map((user) => user.id)).size).toBe(1)
  })

  it('finds a user by our id and by the Firebase uid', async () => {
    const user = await repository.insertOrGet(alice, now)

    expect(await repository.findById(user.id)).toEqual(user)
    expect(await repository.findByFirebaseUid('firebase-alice')).toEqual(user)
    expect(await repository.findByFirebaseUid('firebase-nobody')).toBeUndefined()
  })

  it('saves only the onboarding answers it is given', async () => {
    const user = await repository.insertOrGet(alice, now)
    const later = new Date('2026-10-14T20:00:00Z')

    const updated = await repository.saveOnboarding(
      user.id,
      { displayName: 'Alice', marketingOptIn: true },
      later,
    )

    expect(updated).toMatchObject({
      displayName: 'Alice',
      marketingOptIn: true,
      ageConfirmedAt: null,
      email: alice.email,
      updatedAt: later,
    })
  })

  it('refreshes what the sign-in token says and when the user was last seen', async () => {
    const user = await repository.insertOrGet(alice, now)
    const later = new Date('2026-10-14T21:00:00Z')

    const updated = await repository.updateSignIn(
      user.id,
      { ...alice, email: 'alice@work.example', signInProvider: 'password' },
      later,
    )

    expect(updated).toMatchObject({
      email: 'alice@work.example',
      signInProvider: 'password',
      lastSeenAt: later,
    })
  })

  it('updates nothing for an unknown id', async () => {
    const missing = '00000000-0000-4000-8000-000000000000'

    expect(
      await repository.saveOnboarding(missing, { displayName: 'Ghost' }, new Date()),
    ).toBeUndefined()
  })

  it('keeps the first age confirmation, even when two requests race', async () => {
    const user = await repository.insertOrGet(alice, now)
    const first = new Date('2026-10-14T13:00:00Z')
    const second = new Date('2026-10-14T13:00:01Z')

    await Promise.all([
      repository.saveOnboarding(user.id, { confirmAge: true }, first),
      repository.saveOnboarding(user.id, { confirmAge: true }, second),
    ])
    const confirmedAt = (await repository.findById(user.id))?.ageConfirmedAt
    await repository.saveOnboarding(user.id, { confirmAge: true }, new Date('2026-10-15T00:00Z'))

    expect([first.getTime(), second.getTime()]).toContain(confirmedAt?.getTime())
    expect((await repository.findById(user.id))?.ageConfirmedAt).toEqual(confirmedAt)
  })

  it('stamps the terms acceptance once per version', async () => {
    const user = await repository.insertOrGet(alice, now)
    const accepted = new Date('2026-10-14T13:00:00Z')
    const later = new Date('2026-10-20T09:00:00Z')

    await repository.saveOnboarding(user.id, { acceptTermsVersion: 'v1' }, accepted)
    const again = await repository.saveOnboarding(user.id, { acceptTermsVersion: 'v1' }, later)
    const newVersion = await repository.saveOnboarding(user.id, { acceptTermsVersion: 'v2' }, later)

    expect(again).toMatchObject({ termsVersion: 'v1', termsAcceptedAt: accepted })
    expect(newVersion).toMatchObject({ termsVersion: 'v2', termsAcceptedAt: later })
  })

  it('deletes a user once and reports a repeated delete', async () => {
    const user = await repository.insertOrGet(alice, now)

    expect(await repository.delete(user.id)).toBe(true)
    expect(await repository.delete(user.id)).toBe(false)
    expect(await repository.findById(user.id)).toBeUndefined()
  })
})
