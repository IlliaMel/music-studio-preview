import { readdir } from 'node:fs/promises'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { LocalAudioStorage } from '../src/core/storage/audio-storage.js'
import { TERMS_VERSION } from '../src/features/users/users.service.js'
import { createTestApp, signedInAs } from './support/app.js'
import { createTestDatabase } from './support/database.js'

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
    storage.broken = false
    const retried = await app.request('/v1/me', signedInAs('alice', { method: 'DELETE' }))

    expect(failed.status).toBe(500)
    expect(retried.status).toBe(204)
    expect((await readdir(audioDir)).filter((name) => name.endsWith('.mp3'))).toEqual([])
  })
})
