import type { Database } from '../../core/db/database.js'
import type { Feature } from '../../core/http/app-env.js'
import type { Logger } from '../../core/logger.js'
import type { AudioStorage } from '../../core/storage/audio-storage.js'
import { GenerationWorker, type GenerationWorkerOptions } from './generation/generation-worker.js'
import type { MusicProvider } from './generation/music-provider.js'
import { TracksRepository } from './tracks.repository.js'
import { tracksRoutes } from './tracks.routes.js'
import { type TracksOptions, TracksService } from './tracks.service.js'

export type TracksFeatureOptions = TracksOptions & {
  worker: GenerationWorkerOptions
  /** Base of the audio links, e.g. https://api.example.com; the request's origin when unset. */
  publicBaseUrl?: string
}

export function createTracksFeature(deps: {
  db: Database
  log: Logger
  storage: AudioStorage
  provider: MusicProvider
  options: TracksFeatureOptions
}) {
  const repository = new TracksRepository(deps.db)
  const worker = new GenerationWorker(
    { repository, provider: deps.provider, storage: deps.storage, log: deps.log },
    deps.options.worker,
  )
  const service = new TracksService(
    repository,
    deps.storage,
    worker,
    deps.options,
    undefined,
    deps.provider,
  )
  const feature: Feature = {
    name: 'tracks',
    routes: tracksRoutes(service, deps.options.publicBaseUrl),
  }
  return { feature, service, worker }
}
