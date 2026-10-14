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

const alice: SignInIdentity = {
  firebaseUid: 'firebase-alice',
  email: 'alice@example.com',
  emailVerified: true,
  signInProvider: 'google.com',
}

describe('UsersRepository', () => {
  it('creates a user with an empty profile on first sign-in', async () => {
    const user = await repository.insertOrGet(alice)

    expect(user).toMatchObject({
      ...alice,
      id: expect.stringMatching(/^[0-9a-f-]{36}$/),
      displayName: null,
      ageConfirmedAt: null,
      termsVersion: null,
      marketingOptIn: false,
    })
    expect(user.createdAt).toBeInstanceOf(Date)
  })

  it('returns the existing user instead of creating a second one', async () => {
    const first = await repository.insertOrGet(alice)
    const second = await repository.insertOrGet({ ...alice, email: 'changed@example.com' })

    expect(second.id).toBe(first.id)
    expect(second.email).toBe('alice@example.com')
  })

  it('creates one user when the first requests arrive at the same time', async () => {
    const results = await Promise.all([1, 2, 3].map(() => repository.insertOrGet(alice)))

    expect(new Set(results.map((user) => user.id)).size).toBe(1)
  })

  it('finds a user by our id and by the Firebase uid', async () => {
    const user = await repository.insertOrGet(alice)

    expect(await repository.findById(user.id)).toEqual(user)
    expect(await repository.findByFirebaseUid('firebase-alice')).toEqual(user)
    expect(await repository.findByFirebaseUid('firebase-nobody')).toBeUndefined()
  })

  it('updates only the profile fields it is given', async () => {
    const user = await repository.insertOrGet(alice)
    const now = new Date('2026-10-14T20:00:00Z')

    const updated = await repository.updateProfile(
      user.id,
      { displayName: 'Alice', marketingOptIn: true },
      now,
    )

    expect(updated).toMatchObject({
      displayName: 'Alice',
      marketingOptIn: true,
      ageConfirmedAt: null,
      email: alice.email,
      updatedAt: now,
    })
  })

  it('refreshes what the sign-in token says and when the user was last seen', async () => {
    const user = await repository.insertOrGet(alice)
    const now = new Date('2026-10-14T21:00:00Z')

    const updated = await repository.updateSignIn(
      user.id,
      { ...alice, email: 'alice@work.example', signInProvider: 'password' },
      now,
    )

    expect(updated).toMatchObject({
      email: 'alice@work.example',
      signInProvider: 'password',
      lastSeenAt: now,
    })
  })

  it('updates nothing for an unknown id', async () => {
    const missing = '00000000-0000-4000-8000-000000000000'

    expect(await repository.updateProfile(missing, { displayName: 'Ghost' }, new Date())).toBe(
      undefined,
    )
  })

  it('deletes a user once and reports a repeated delete', async () => {
    const user = await repository.insertOrGet(alice)

    expect(await repository.delete(user.id)).toBe(true)
    expect(await repository.delete(user.id)).toBe(false)
    expect(await repository.findById(user.id)).toBeUndefined()
  })
})
