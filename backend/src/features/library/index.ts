import type { Database } from '../../core/db/database.js'
import type { Feature } from '../../core/http/app-env.js'
import { LibraryRepository } from './library.repository.js'
import { libraryRoutes } from './library.routes.js'
import { LibraryService } from './library.service.js'

export function createLibraryFeature(db: Database, publicBaseUrl?: string): Feature {
  const service = new LibraryService(new LibraryRepository(db))
  return { name: 'library', routes: libraryRoutes(service, publicBaseUrl) }
}
