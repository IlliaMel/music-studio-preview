import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { composeApp } from '../src/compose.js'
import {
  type AuthProvider,
  InvalidTokenError,
  type VerifiedIdentity,
} from '../src/core/auth/auth-provider.js'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { LocalAudioStorage } from '../src/core/storage/audio-storage.js'
import { uploads } from '../src/features/library/library.table.js'
import { MockMusicProvider } from '../src/features/tracks/generation/mock-music-provider.js'
import { tracks } from '../src/features/tracks/tracks.table.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { users } from '../src/features/users/users.table.js'
import { createTestDatabase, truncateAll } from './support/database.js'
import { silentLogger } from './support/logger.js'

let database: DatabaseHandle
const directories: string[] = []
beforeAll(async () => {
  database = await createTestDatabase()
})
beforeEach(() => truncateAll(database.db))
afterAll(async () => {
  await database.close()
  await Promise.all(directories.map((directory) => rm(directory, { recursive: true, force: true })))
})

class ClaimsAuth implements AuthProvider {
  readonly deleted: string[] = []
  readonly identities: Record<string, VerifiedIdentity> = {
    allowed: {
      uid: 'allowed-uid',
      email: 'MuSiC@Example.COM',
      emailVerified: true,
      signInProvider: 'google.com',
    },
    denied: {
      uid: 'denied-uid',
      email: 'other@example.com',
      emailVerified: true,
      signInProvider: 'google.com',
    },
    unverified: {
      uid: 'unverified-uid',
      email: 'music@example.com',
      emailVerified: false,
      signInProvider: 'password',
    },
    missing: { uid: 'missing-uid', email: null, emailVerified: true, signInProvider: 'apple.com' },
  }

  async verifyIdToken(token: string): Promise<VerifiedIdentity> {
    const identity = this.identities[token]
    if (!identity) throw new InvalidTokenError('unknown fixture token')
    return identity
  }

  async deleteAccount(uid: string): Promise<void> {
    this.deleted.push(uid)
  }
}

async function fixture(
  options: { allowedEmails?: readonly string[]; firebaseDeleteUsers?: boolean } = {},
) {
  const directory = await mkdtemp(join(tmpdir(), 'studio-generation-access-'))
  directories.push(directory)
  const auth = new ClaimsAuth()
  const storage = new LocalAudioStorage(directory)
  const composed = composeApp({
    db: database.db,
    log: silentLogger,
    auth,
    storage,
    provider: new MockMusicProvider(0),
    generationAllowedEmails: options.allowedEmails,
    firebaseDeleteUsers: options.firebaseDeleteUsers,
    tracks: {
      dailyLimit: 25,
      defaultModel: 'music_v2_5',
      publicBaseUrl: 'https://api.example.test',
      worker: { concurrency: 1, timeoutMs: 2000, retryDelaysMs: [], pollIntervalMs: 50 },
    },
  })
  const request = (path: string, token: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers)
    headers.set('Authorization', `Bearer ${token}`)
    return composed.app.request(`/v1${path}`, { ...init, headers })
  }
  const acceptTerms = (token: string) =>
    request('/me', token, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ acceptedTermsVersion: TERMS_VERSION }),
    })
  const create = (token: string) =>
    request('/tracks', token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `generation-${token}` },
      body: JSON.stringify({ prompt: 'Warm piano with gentle strings', lengthMs: 30000 }),
    })
  const upload = (token: string) =>
    request('/uploads', token, {
      method: 'POST',
      headers: { 'Content-Type': 'audio/wav' },
      body: new Uint8Array([1, 2, 3]),
    })
  return { ...composed, auth, storage, request, acceptTerms, create, upload }
}

describe('verified-email generation access', () => {
  it('accepts a verified email case-insensitively and reports access on both profile endpoints', async () => {
    const app = await fixture({ allowedEmails: [' music@example.com '] })
    expect(await (await app.request('/me', 'allowed')).json()).toMatchObject({ canGenerate: true })
    expect(await (await app.acceptTerms('allowed')).json()).toMatchObject({ canGenerate: true })
    expect((await app.create('allowed')).status).toBe(202)
    expect((await app.upload('allowed')).status).toBe(201)
  })

  it.each(['denied', 'unverified', 'missing'])(
    'denies %s without creating tracks or storing upload bytes',
    async (token) => {
      const app = await fixture({ allowedEmails: ['music@example.com'] })
      expect(await (await app.request('/me', token)).json()).toMatchObject({ canGenerate: false })
      expect(await (await app.acceptTerms(token)).json()).toMatchObject({ canGenerate: false })
      for (const response of [await app.create(token), await app.upload(token)]) {
        expect(response.status).toBe(403)
        expect(await response.json()).toMatchObject({ error: { code: 'GENERATION_NOT_ALLOWED' } })
      }
      expect(await database.db.select().from(tracks)).toEqual([])
      expect(await database.db.select().from(uploads)).toEqual([])
      expect((await app.request('/catalog', token)).status).toBe(200)
      expect((await app.request('/likes', token)).status).toBe(200)
    },
  )

  it('checks current verified claims on every request rather than trusting cached profile access', async () => {
    const app = await fixture({ allowedEmails: ['music@example.com'] })
    await app.acceptTerms('allowed')
    const original = app.auth.identities.allowed
    if (!original) throw new Error('Missing allowed identity fixture')
    app.auth.identities.allowed = { ...original, emailVerified: false }
    expect(await (await app.request('/me', 'allowed')).json()).toMatchObject({ canGenerate: false })
    expect((await app.create('allowed')).status).toBe(403)
    app.auth.identities.allowed = { ...original, email: 'elsewhere@example.com' }
    expect((await app.upload('allowed')).status).toBe(403)
    app.auth.identities.allowed = original
    expect((await app.create('allowed')).status).toBe(202)
  })

  it('retains unrestricted legacy behavior when no allowlist is configured, including unverified or missing email', async () => {
    const app = await fixture()
    for (const token of ['unverified', 'missing']) {
      expect(await (await app.acceptTerms(token)).json()).toMatchObject({ canGenerate: true })
      expect((await app.create(token)).status).toBe(202)
      expect((await app.upload(token)).status).toBe(201)
    }
  })

  it('rejects generation before request validation but still requires a valid sign-in token', async () => {
    const app = await fixture({ allowedEmails: ['music@example.com'] })
    const denied = await app.request('/tracks', 'denied', { method: 'POST', body: 'invalid json' })
    expect(denied.status).toBe(403)
    expect(await denied.json()).toMatchObject({ error: { code: 'GENERATION_NOT_ALLOWED' } })
    expect((await app.request('/tracks', 'unknown', { method: 'POST' })).status).toBe(401)
  })
})

describe('shared Firebase deletion policy', () => {
  it.each([undefined, true, false])(
    'purges local rows and audio with Firebase deletion set to %s',
    async (firebaseDeleteUsers) => {
      const app = await fixture({ firebaseDeleteUsers })
      await app.acceptTerms('allowed')
      const created = await app.create('allowed')
      expect(created.status).toBe(202)
      const body = (await created.json()) as { id: string }
      await app.worker.runOnce()
      expect(await app.storage.open(body.id)).toBeDefined()
      expect((await app.request('/me', 'allowed', { method: 'DELETE' })).status).toBe(204)
      expect(app.auth.deleted).toEqual(firebaseDeleteUsers === false ? [] : ['allowed-uid'])
      expect(await database.db.select().from(users)).toEqual([])
      expect(await database.db.select().from(tracks)).toEqual([])
      expect(await app.storage.open(body.id)).toBeUndefined()
    },
  )
})
