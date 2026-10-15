import { createHash } from 'node:crypto'
import { validAudio } from '../../core/audio/audio-container.js'
import { ApiError } from '../../core/http/errors.js'
import type { AudioStorage } from '../../core/storage/audio-storage.js'
import { type RecordingsRepository, recordingNotFound } from './recordings.repository.js'

export class RecordingsService {
  constructor(
    private readonly repository: RecordingsRepository,
    private readonly storage: AudioStorage,
  ) {}
  async list(userId: string) {
    await this.reclaimStalled(userId)
    return this.repository.list(userId)
  }
  async save(
    userId: string,
    id: string,
    title: string,
    durationMs: number,
    contentType: string,
    audio: Buffer,
  ) {
    if (audio.length > 20 * 1024 * 1024)
      throw new ApiError(413, 'PAYLOAD_TOO_LARGE', 'The recording exceeds 20 MB')
    if (!['audio/mp4', 'audio/mpeg', 'audio/wav', 'audio/x-wav'].includes(contentType))
      throw new ApiError(415, 'UNSUPPORTED_AUDIO', 'Send an MP4/M4A, MP3 or WAV recording')
    if (!validAudio(audio, contentType))
      throw new ApiError(422, 'INVALID_RECORDING', 'The recording container is invalid or empty')
    await this.reclaimStalled(userId)
    const reservation = await this.repository.reserve(userId, {
      id,
      originalTitle: title,
      durationMs,
      contentType,
      sizeBytes: audio.length,
      sha256: createHash('sha256').update(audio).digest('hex'),
    })
    const row = await this.repository.complete(userId, id, () =>
      this.storage.save(recordingKey(userId, id), audio),
    )
    return { row, created: reservation.created }
  }
  rename(userId: string, id: string, title: string) {
    return this.repository.rename(userId, id, title)
  }
  remove(userId: string, id: string) {
    return this.repository.remove(userId, id, () => this.storage.delete(recordingKey(userId, id)))
  }
  async audio(userId: string, id: string) {
    const row = await this.repository.find(userId, id)
    if (!row || row.deletedAt || !row.ready) throw recordingNotFound()
    const file = await this.storage.open(recordingKey(userId, id))
    if (!file) throw new ApiError(410, 'AUDIO_GONE', 'The recording file is no longer available')
    return { file, contentType: row.contentType }
  }
  private reclaimStalled(userId: string) {
    return this.repository.reclaimStalled(userId, (row) =>
      this.storage.delete(recordingKey(userId, row.id)),
    )
  }
  async deleteAllForUser(userId: string): Promise<void> {
    const rows = await this.repository.allForDeletion(userId)
    await Promise.all(rows.map((row) => this.storage.delete(recordingKey(userId, row.id))))
  }
}
export const recordingKey = (userId: string, id: string) => `recording-${userId}-${id}`
