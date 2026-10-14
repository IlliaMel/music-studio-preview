import { createApp } from './app.js'
import type { AuthProvider } from './core/auth/auth-provider.js'
import { authenticate } from './core/auth/authenticate.js'
import type { Database } from './core/db/database.js'
import type { Feature } from './core/http/app-env.js'
import type { Logger } from './core/logger.js'
import type { AudioStorage } from './core/storage/audio-storage.js'
import type { MusicProvider } from './features/tracks/generation/music-provider.js'
import { createTracksFeature, type TracksFeatureOptions } from './features/tracks/index.js'
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
  const users = createUsersFeature({
    db,
    auth,
    onAccountDeleted: (userId) => tracks.service.deleteAllForUser(userId),
  })
  const features = [users.feature, tracks.feature, ...extraFeatures]

  const app = createApp({
    db,
    log,
    authenticate: authenticate(auth, (identity) => users.service.resolve(identity)),
    features,
  })
  return { app, features, worker: tracks.worker }
}
