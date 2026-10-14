import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { VerifiedIdentity } from '../src/core/auth/auth-provider.js'
import { DevAuthProvider } from '../src/core/auth/dev-auth-provider.js'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { UsersRepository } from '../src/features/users/users.repository.js'
import { TERMS_VERSION, UsersService } from '../src/features/users/users.service.js'
import { createTestDatabase, truncateAll } from './support/database.js'

let database: DatabaseHandle
let repository: UsersRepository
let now: Date
let service: UsersService

beforeAll(async () => {
  database = await createTestDatabase()
  repository = new UsersRepository(database.db)
})

beforeEach(async () => {
  await truncateAll(database.db)
  now = new Date('2026-10-14T12:00:00Z')
  service = new UsersService(repository, new DevAuthProvider(), () => now)
})

afterAll(() => database.close())

const alice: VerifiedIdentity = {
  uid: 'firebase-alice',
  email: 'alice@example.com',
  emailVerified: true,
  signInProvider: 'google.com',
}

const minutesLater = (minutes: number) => new Date(now.getTime() + minutes * 60_000)

describe('resolve', () => {
  it('does not write on every request: last seen moves at most once an hour', async () => {
    const user = await service.resolve(alice)
    const created = await repository.findById(user.id)

    now = minutesLater(59)
    await service.resolve(alice)
    const withinTheHour = await repository.findById(user.id)
    now = minutesLater(1)
    await service.resolve(alice)
    const afterTheHour = await repository.findById(user.id)

    expect(withinTheHour?.lastSeenAt).toEqual(created?.lastSeenAt)
    expect(afterTheHour?.lastSeenAt).toEqual(now)
  })

  it('picks up a changed email right away', async () => {
    const user = await service.resolve(alice)

    await service.resolve({ ...alice, email: 'alice@work.example' })

    expect((await repository.findById(user.id))?.email).toBe('alice@work.example')
  })
})

describe('updateProfile', () => {
  it('keeps the time of the first age confirmation', async () => {
    const { id } = await service.resolve(alice)
    const firstConfirmation = now

    await service.updateProfile(id, { ageConfirmed: true })
    now = minutesLater(30)
    const user = await service.updateProfile(id, { ageConfirmed: true })

    expect(user.ageConfirmedAt).toEqual(firstConfirmation)
  })

  it('keeps the time the current terms were first accepted', async () => {
    const { id } = await service.resolve(alice)
    const firstAcceptance = now

    await service.updateProfile(id, { acceptedTermsVersion: TERMS_VERSION })
    now = minutesLater(30)
    const user = await service.updateProfile(id, { acceptedTermsVersion: TERMS_VERSION })

    expect(user.termsAcceptedAt).toEqual(firstAcceptance)
  })
})
