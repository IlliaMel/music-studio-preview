import type { Database } from '../../../core/db/database.js'
import { ApiError } from '../../../core/http/errors.js'
import { LibraryRepository } from '../../library/library.repository.js'
import { TracksRepository } from '../tracks.repository.js'
import type { ReferenceAccess } from './elevenlabs-music-provider.js'
import { ProviderError } from './music-provider.js'

export function referenceAccess(db: Database, maxPaidRequestsTotal?: number): ReferenceAccess {
  const library = new LibraryRepository(db)
  const tracks = new TracksRepository(db)
  return {
    async resolve(request) {
      const userId = request.identity?.userId
      if (!userId)
        throw new ProviderError(
          'INVALID_REFERENCE',
          'A reference requires a persisted generation identity',
          false,
        )
      if (request.referenceUploadId) {
        const upload = await library.findUpload(userId, request.referenceUploadId)
        if (!upload)
          throw new ProviderError(
            'INVALID_REFERENCE',
            'Reference upload is no longer available',
            false,
          )
        return {
          key: upload.id,
          audio: Buffer.from(upload.data, 'base64'),
          contentType: upload.contentType,
          songId: upload.providerSongId ?? undefined,
          durationMs: upload.durationMs ?? undefined,
          attempted: upload.providerAttempted,
        }
      }
      if (request.remixTrackId) {
        const source = await tracks.findForUser(userId, request.remixTrackId)
        if (
          source?.status !== 'done' ||
          source.providerName !== 'elevenlabs' ||
          !source.songId ||
          !source.durationMs
        )
          throw new ProviderError(
            'UNSUPPORTED_MODE',
            'Live remix requires your completed ElevenLabs original stored while references were enabled; mock catalog tracks cannot be remixed live',
            false,
          )
        return {
          key: source.id,
          songId: source.songId,
          durationMs: source.durationMs,
          original: {
            prompt: source.prompt,
            genres: source.genres ?? [],
            words: source.words ?? [],
          },
        }
      }
      throw new ProviderError('INVALID_REFERENCE', 'Choose a reference source', false)
    },
    async claim(reference) {
      try {
        const attemptId = crypto.randomUUID()
        const claimed = await library.claimRemoteUpload(
          reference.key,
          maxPaidRequestsTotal,
          attemptId,
        )
        if (claimed) reference.attemptId = attemptId
        return claimed
      } catch (error) {
        if (error instanceof ApiError && error.code === 'SERVICE_DAILY_LIMIT') {
          throw new ProviderError('SERVICE_DAILY_LIMIT', error.message, false)
        }
        throw error
      }
    },
    save: (reference, songId, durationMs) =>
      library.saveRemoteUpload(reference.key, songId, durationMs, reference.attemptId),
    rejected: (reference) => library.rejectRemoteUpload(reference.key, reference.attemptId),
  }
}
