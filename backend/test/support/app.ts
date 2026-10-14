import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { composeApp } from '../../src/compose.js'
import type { AuthProvider } from '../../src/core/auth/auth-provider.js'
import { DevAuthProvider } from '../../src/core/auth/dev-auth-provider.js'
import type { Database } from '../../src/core/db/database.js'
import type { Feature } from '../../src/core/http/app-env.js'
import { LocalAudioStorage } from '../../src/core/storage/audio-storage.js'
import { MockMusicProvider } from '../../src/features/tracks/generation/mock-music-provider.js'
import type { MusicProvider } from '../../src/features/tracks/generation/music-provider.js'
import { silentLogger } from './logger.js'

/** Dev sign-in that remembers which accounts the server asked Firebase to delete. */
export class RecordingAuthProvider extends DevAuthProvider {
  readonly deletedAccounts: string[] = []

  override async deleteAccount(uid: string): Promise<void> {
    this.deletedAccounts.push(uid)
  }
}

/**
 * The app wired by the same `composeApp` as `main.ts`, with dev sign-in, a mock provider that
 * answers at once and audio in a temporary folder. The worker is not started: tests run queued
 * generations one at a time with `worker.runOnce()`, so nothing happens behind their back.
 */
export async function createTestApp(
  db: Database,
  {
    auth = new RecordingAuthProvider(),
    provider = new MockMusicProvider(0),
    features = [],
    dailyLimit = 8,
    storage: makeStorage = (dir: string) => new LocalAudioStorage(dir),
  }: {
    auth?: AuthProvider
    provider?: MusicProvider
    features?: Feature[]
    dailyLimit?: number
    storage?: (audioDir: string) => LocalAudioStorage
  } = {},
) {
  const audioDir = await mkdtemp(join(tmpdir(), 'studio-audio-'))
  const storage = makeStorage(audioDir)
  const { app, worker } = composeApp(
    {
      db,
      log: silentLogger,
      auth,
      storage,
      provider,
      tracks: {
        dailyLimit,
        defaultModel: 'music_v2_5',
        publicBaseUrl: 'https://api.studio.test',
        worker: { concurrency: 1, timeoutMs: 2_000, retryDelaysMs: [1, 1, 1], pollIntervalMs: 50 },
      },
    },
    features,
  )
  return { app, auth, worker, storage, audioDir }
}

/** Request options for a user signed in with the dev token `dev:<name>`. */
export function signedInAs(
  name: string,
  {
    method = 'GET',
    body,
    headers = {},
  }: { method?: string; body?: unknown; headers?: Record<string, string> } = {},
): RequestInit {
  return {
    method,
    headers: {
      Authorization: `Bearer dev:${name}`,
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...headers,
    },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  }
}
