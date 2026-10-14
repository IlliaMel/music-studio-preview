import type { CurrentUser } from '../../core/http/app-env.js'
import { ApiError } from '../../core/http/errors.js'
import type { AudioStorage, StoredFile } from '../../core/storage/audio-storage.js'
import type { ModelId } from './generation/music-provider.js'
import type { TrackCursor, TracksRepository } from './tracks.repository.js'
import type { Track } from './tracks.table.js'

export type CreateTrackInput = {
  prompt: string
  lengthMs?: number
  instrumental: boolean
  modelId?: ModelId
}

export type Quota = { limit: number; used: number; remaining: number; resetsAt: Date }

export type TracksOptions = {
  /** Generations per user per day (UTC), to protect the ElevenLabs credits. */
  dailyLimit: number
  defaultModel: ModelId
}

export class TracksService {
  constructor(
    private readonly repository: TracksRepository,
    private readonly storage: AudioStorage,
    private readonly worker: { wake(): void },
    private readonly options: TracksOptions,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /**
   * Queues a generation. The same idempotency key returns the same track, so the app can
   * safely retry the request after a dropped connection without paying twice.
   */
  async create(
    user: CurrentUser,
    idempotencyKey: string,
    input: CreateTrackInput,
  ): Promise<{ track: Track; created: boolean }> {
    const now = this.now()
    const outcome = await this.repository.create(
      {
        userId: user.id,
        idempotencyKey,
        prompt: input.prompt,
        lengthMs: input.lengthMs ?? null,
        instrumental: input.instrumental,
        modelId: input.modelId ?? this.options.defaultModel,
      },
      { since: startOfDay(now), max: this.options.dailyLimit },
      now,
    )
    switch (outcome.kind) {
      case 'limit-reached':
        throw new ApiError(429, 'DAILY_LIMIT', 'No generations left today', {
          limit: this.options.dailyLimit,
          resetsAt: startOfNextDay(now).toISOString(),
        })
      case 'existing':
        return { track: outcome.track, created: false }
      case 'created':
        this.worker.wake()
        return { track: outcome.track, created: true }
    }
  }

  async get(user: CurrentUser, id: string): Promise<Track> {
    const track = await this.repository.findForUser(user.id, id)
    if (!track) throw trackNotFound()
    return track
  }

  async list(
    user: CurrentUser,
    limit: number,
    cursor?: TrackCursor,
  ): Promise<{ tracks: Track[]; next?: TrackCursor }> {
    // One extra row tells whether there is another page, without a second query.
    const rows = await this.repository.listForUser(user.id, limit + 1, cursor)
    const tracks = rows.slice(0, limit)
    const last = tracks.at(-1)
    return {
      tracks,
      next: rows.length > limit && last ? { createdAt: last.createdAt, id: last.id } : undefined,
    }
  }

  async audio(user: CurrentUser, id: string): Promise<StoredFile> {
    const track = await this.get(user, id)
    if (track.status !== 'done') {
      throw new ApiError(409, 'NOT_READY', `The track is ${track.status}`, { status: track.status })
    }
    const file = await this.storage.open(track.id)
    if (!file) throw new ApiError(410, 'AUDIO_GONE', 'The audio file is no longer available')
    return file
  }

  /** Works in any state. A track being generated finishes in the background and is discarded. */
  async delete(user: CurrentUser, id: string): Promise<void> {
    if (!(await this.repository.deleteForUser(user.id, id))) throw trackNotFound()
    await this.storage.delete(id)
  }

  async quota(user: CurrentUser): Promise<Quota> {
    const now = this.now()
    const used = await this.repository.countSince(user.id, startOfDay(now))
    const limit = this.options.dailyLimit
    return { limit, used, remaining: Math.max(0, limit - used), resetsAt: startOfNextDay(now) }
  }

  /** Part of deleting an account: the rows and every audio file. */
  async deleteAllForUser(userId: string): Promise<void> {
    const ids = await this.repository.deleteAllForUser(userId)
    await Promise.all(ids.map((id) => this.storage.delete(id)))
  }
}

const trackNotFound = () => new ApiError(404, 'TRACK_NOT_FOUND', 'No such track')

const startOfDay = (time: Date) =>
  new Date(Date.UTC(time.getUTCFullYear(), time.getUTCMonth(), time.getUTCDate()))

const startOfNextDay = (time: Date) => new Date(startOfDay(time).getTime() + 24 * 60 * 60 * 1000)
