import type { Database } from '../../core/db/database.js'
import type { AudioStorage } from '../../core/storage/audio-storage.js'
import { RecordingsRepository } from './recordings.repository.js'
import { recordingsRoutes } from './recordings.routes.js'
import { RecordingsService } from './recordings.service.js'
export function createRecordingsFeature(
  db: Database,
  storage: AudioStorage,
  publicBaseUrl?: string,
) {
  const service = new RecordingsService(new RecordingsRepository(db), storage)
  return {
    service,
    feature: { name: 'recordings', routes: recordingsRoutes(service, publicBaseUrl) },
  }
}
