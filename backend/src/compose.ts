import { Hono } from 'hono'
import { createApp } from './app.js'
import type { AuthProvider } from './core/auth/auth-provider.js'
import { authenticate } from './core/auth/authenticate.js'
import type { Database } from './core/db/database.js'
import type { AppEnv, Feature } from './core/http/app-env.js'
import type { Logger } from './core/logger.js'
import type { AudioStorage } from './core/storage/audio-storage.js'
import { createLibraryFeature } from './features/library/index.js'
import { LibraryRepository } from './features/library/library.repository.js'
import { createRecordingsFeature } from './features/recordings/index.js'
import type { MusicProvider } from './features/tracks/generation/music-provider.js'
import { createTracksFeature, type TracksFeatureOptions } from './features/tracks/index.js'
import { AccountDeletionWorker } from './features/users/account-deletion-worker.js'
import { createUsersFeature } from './features/users/index.js'

export type AppServices = {
  db: Database
  log: Logger
  auth: AuthProvider
  storage: AudioStorage
  provider: MusicProvider
  tracks: TracksFeatureOptions
}

/**
 * Creates every feature and wires them into the HTTP app. `main.ts` calls it with the real
 * services and the tests with an in-memory database, so both run exactly the same wiring.
 * `extraFeatures` lets a test mount its own probe routes behind the same middleware.
 */
export function composeApp(services: AppServices, extraFeatures: readonly Feature[] = []) {
  const { db, log, auth } = services
  const tracks = createTracksFeature({
    db,
    log,
    storage: services.storage,
    provider: services.provider,
    options: services.tracks,
  })
  const recordings = createRecordingsFeature(db, services.storage, services.tracks.publicBaseUrl)
  const users = createUsersFeature({
    db,
    auth,
    onAccountDeleted: async (userId) => {
      await tracks.service.deleteAllForUser(userId)
      await recordings.service.deleteAllForUser(userId)
    },
  })
  const features = [
    users.feature,
    tracks.feature,
    recordings.feature,
    createLibraryFeature(
      db,
      services.tracks.publicBaseUrl,
      services.provider.name === 'elevenlabs',
    ),
    {
      name: 'capabilities',
      routes: new Hono<AppEnv>().get('/capabilities', (c) =>
        c.json({
          provider: services.provider.name,
          qualityModes: services.provider.paid ? ['auto'] : ['auto', 'deep', 'fast'],
          customLyrics: true,
          variants: [1, 2],
          audioReferences: services.provider.paid
            ? services.provider.capabilities?.references
              ? 'enabled-enterprise'
              : 'disabled-enterprise-entitlement-required'
            : 'mock',
          liveCatalogRemix: false,
          maxRemixSources: services.provider.paid
            ? services.provider.capabilities?.references
              ? 1
              : 0
            : 4,
          remixOutput: services.provider.paid
            ? 'single-source-conditioning'
            : 'fixture-no-audio-blending',
          uploads: {
            maxFiles: 20,
            maxBytes: 100 * 1024 * 1024,
            maxFileBytes: 20 * 1024 * 1024,
            ttlSeconds: 86400,
          },
          recordings: {
            maxFiles: 20,
            maxBytes: 100 * 1024 * 1024,
            maxFileBytes: 20 * 1024 * 1024,
            maxDurationMs: 600000,
            retention: 'until-deleted',
          },
          uncertainPaidRequests: 'terminal-no-automatic-retry',
        }),
      ),
    },
    ...extraFeatures,
  ]

  const app = createApp({
    db,
    log,
    authenticate: authenticate(auth, (identity) => users.service.resolve(identity)),
    features,
  })
  return {
    app,
    features,
    purgeExpiredUploads: () => new LibraryRepository(db).purgeExpiredUploads(),
    worker: tracks.worker,
    accountDeletionWorker: new AccountDeletionWorker(users.service, log),
  }
}
