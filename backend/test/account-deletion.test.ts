import { readdir } from 'node:fs/promises'
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { InvalidTokenError } from '../src/core/auth/auth-provider.js'
import { DevAuthProvider } from '../src/core/auth/dev-auth-provider.js'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { LocalAudioStorage } from '../src/core/storage/audio-storage.js'
import { likes, playlists, uploads } from '../src/features/library/library.table.js'
import { MockMusicProvider } from '../src/features/tracks/generation/mock-music-provider.js'
import { UsersRepository } from '../src/features/users/users.repository.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase, truncateAll } from './support/database.js'

/** Storage whose deletes fail while `broken` is set, like a busy or read-only volume. */
class FlakyStorage extends LocalAudioStorage {
  broken = false

  override async delete(key: string): Promise<void> {
    if (this.broken) throw Object.assign(new Error('EBUSY: resource busy'), { code: 'EBUSY' })
    return super.delete(key)
  }
}

let database: DatabaseHandle

beforeAll(async () => {
  database = await createTestDatabase()
})

beforeEach(() => truncateAll(database.db))
afterAll(() => database.close())

describe('deleting an account', () => {
  it('can be retried when an audio file could not be removed, and then removes it', async () => {
    const storages: FlakyStorage[] = []
    const { app, worker, audioDir } = await createTestApp(database.db, {
      storage: (dir) => {
        const storage = new FlakyStorage(dir)
        storages.push(storage)
        return storage
      },
    })
    const storage = storages[0] as FlakyStorage
    await app.request(
      '/v1/me',
      signedInAs('alice', {
        method: 'PATCH',
        body: { displayName: 'Alice', ageConfirmed: true, acceptedTermsVersion: TERMS_VERSION },
      }),
    )
    await app.request(
      '/v1/tracks',
      signedInAs('alice', {
        method: 'POST',
        body: { prompt: 'A song to keep on the disk for a while' },
        headers: { 'Idempotency-Key': 'flaky-0001' },
      }),
    )
    await worker.runOnce()

    storage.broken = true
    const failed = await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))
    expect((await app.request('/v1/me', signedInAs('alice'))).status).toBe(409)
    expect(
      (
        await app.request(
          '/v1/tracks',
          signedInAs('alice', {
            method: 'POST',
            body: { prompt: 'Do not accept a generation during deletion' },
            headers: { 'Idempotency-Key': 'deleting-generation' },
          }),
        )
      ).status,
    ).toBe(409)
    storage.broken = false
    const retried = await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))

    expect(failed.status).toBe(500)
    expect(retried.status).toBe(204)
    expect(await readdir(audioDir)).toEqual([])
  })
})

async function queueFor(app: Awaited<ReturnType<typeof createTestApp>>['app']) {
  await app.request(
    '/v1/me',
    signedInAs('alice', { method: 'PATCH', body: { acceptedTermsVersion: TERMS_VERSION } }),
  )
  const response = await app.request(
    '/v1/tracks',
    signedInAs('alice', {
      method: 'POST',
      body: { prompt: 'A song for concurrent account deletion' },
      headers: { 'Idempotency-Key': 'concurrent-account' },
    }),
  )
  expect(response.status).toBe(202)
  return (await response.json()) as { id: string }
}

it.each(['account', 'track'] as const)(
  'does not report %s deletion complete while a worker is saving audio',
  async (target) => {
    let saveStarted: () => void = () => {}
    let releaseSave: () => void = () => {}
    const started = new Promise<void>((resolve) => {
      saveStarted = resolve
    })
    const release = new Promise<void>((resolve) => {
      releaseSave = resolve
    })
    class PausedStorage extends LocalAudioStorage {
      override async save(key: string, audio: Uint8Array) {
        saveStarted()
        await release
        await super.save(key, audio)
      }
    }
    const { app, worker, audioDir } = await createTestApp(database.db, {
      storage: (directory) => new PausedStorage(directory),
    })
    const track = await queueFor(app)
    const running = worker.runOnce()
    await started
    let deleted = false
    const deletion = Promise.resolve(
      app.request(
        target === 'account' ? '/v1/me' : `/v1/tracks/${track.id}`,
        signedInAs('alice', { method: 'DELETE' }),
      ),
    ).then((response) => {
      deleted = true
      return response
    })
    try {
      await new Promise((resolve) => setTimeout(resolve, 100))
      expect(deleted).toBe(false)
    } finally {
      releaseSave()
    }
    await running
    expect((await deletion).status).toBe(204)
    expect(await readdir(audioDir)).toEqual([])
  },
)

it('never writes audio when a provider finishes after account deletion', async () => {
  let providerStarted: () => void = () => {}
  let releaseProvider: () => void = () => {}
  const started = new Promise<void>((resolve) => {
    providerStarted = resolve
  })
  const release = new Promise<void>((resolve) => {
    releaseProvider = resolve
  })
  const provider = new MockMusicProvider(0)
  const { app, worker, audioDir } = await createTestApp(database.db, {
    provider: {
      name: 'paused',
      async compose(request, signal) {
        providerStarted()
        await release
        return provider.compose(request, signal)
      },
    },
  })
  await queueFor(app)
  const running = worker.runOnce()
  await started
  expect((await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))).status).toBe(204)
  releaseProvider()
  await running
  expect(await readdir(audioDir).catch(() => [])).toEqual([])
})

it('retains the deletion marker for retry when Firebase deletion fails', async () => {
  class FlakyAuth extends DevAuthProvider {
    broken = true
    override async deleteAccount(uid: string) {
      if (this.broken) throw new Error('Firebase unavailable')
      return super.deleteAccount(uid)
    }
  }
  const auth = new FlakyAuth()
  const { app } = await createTestApp(database.db, { auth })
  await app.request('/v1/me', signedInAs('alice'))
  const repository = new UsersRepository(database.db)
  const original = await repository.findByFirebaseUid('dev-alice')
  expect((await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))).status).toBe(500)
  const pending = await repository.findByFirebaseUid('dev-alice')
  expect(pending?.id).toBe(original?.id)
  expect(pending?.deletionRequestedAt).not.toBeNull()
  expect((await app.request('/v1/me', signedInAs('alice'))).status).toBe(409)
  auth.broken = false
  expect((await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))).status).toBe(204)
  expect(await repository.findByFirebaseUid('dev-alice')).toBeUndefined()
})

it('retries a failed file cleanup after a single track is hidden', async () => {
  let storage: FlakyStorage | undefined
  const { app, worker, audioDir } = await createTestApp(database.db, {
    storage: (directory) => {
      storage = new FlakyStorage(directory)
      return storage
    },
  })
  const track = await queueFor(app)
  await worker.runOnce()
  if (!storage) throw new Error('Storage was not created')
  storage.broken = true
  const path = `/v1/tracks/${track.id}`
  expect((await app.request(path, signedInAs('alice', { method: 'DELETE' }))).status).toBe(500)
  expect((await app.request(path, signedInAs('alice'))).status).toBe(404)
  storage.broken = false
  expect((await app.request(path, signedInAs('alice', { method: 'DELETE' }))).status).toBe(204)
  expect(await readdir(audioDir)).toEqual([])
})

it('recovers without a valid token after Firebase succeeds and the final SQL delete fails', async () => {
  class RevokingAuth extends DevAuthProvider {
    deleted = false
    override async verifyIdToken(token: string) {
      if (this.deleted) throw new InvalidTokenError('revoked')
      return super.verifyIdToken(token)
    }
    override async deleteAccount() {
      this.deleted = true
    }
  }
  const auth = new RevokingAuth()
  const { app, worker, accountDeletionWorker, audioDir } = await createTestApp(database.db, {
    auth,
  })
  const track = await queueFor(app)
  await worker.runOnce()
  await app.request(`/v1/likes/${track.id}`, signedInAs('alice', { method: 'PUT' }))
  await app.request(
    '/v1/playlists',
    signedInAs('alice', { method: 'POST', body: { name: 'Private playlist' } }),
  )
  const repository = new UsersRepository(database.db)
  const deletion = vi
    .spyOn(UsersRepository.prototype, 'delete')
    .mockRejectedValueOnce(new Error('SQL temporarily unavailable'))
  try {
    expect((await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))).status).toBe(
      500,
    )
    expect(auth.deleted).toBe(true)
    expect((await repository.findByFirebaseUid('dev-alice'))?.deletionRequestedAt).not.toBeNull()
    expect((await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))).status).toBe(
      401,
    )
    accountDeletionWorker.start()
    await vi.waitFor(async () =>
      expect(await repository.findByFirebaseUid('dev-alice')).toBeUndefined(),
    )
    await accountDeletionWorker.stop()
    expect(await database.db.select().from(likes)).toEqual([])
    expect(await database.db.select().from(playlists)).toEqual([])
    expect(await database.db.select().from(uploads)).toEqual([])
    expect(await readdir(audioDir)).toEqual([])
  } finally {
    await accountDeletionWorker.stop()
    deletion.mockRestore()
  }
})
