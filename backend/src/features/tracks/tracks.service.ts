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
    const request = {
      prompt: input.prompt,
      lengthMs: input.lengthMs ?? null,
      instrumental: input.instrumental,
      modelId: input.modelId ?? this.options.defaultModel,
    }
    const outcome = await this.repository.create(
      { userId: user.id, idempotencyKey, ...request },
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
        // A retry must be the same request; a reused key with a new prompt is a client bug,
        // and quietly answering with the old track would lose the user's edit.
        if (!sameRequest(outcome.track, request)) {
          throw new ApiError(
            422,
            'IDEMPOTENCY_KEY_REUSED',
            'This Idempotency-Key was used for a different request; use a new key per attempt',
          )
        }
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

  /**
   * Works in any state; a track still generating has its result thrown away when it arrives.
   * The file goes first: if that fails, the track is still listed and the delete can be retried.
   */
  async delete(user: CurrentUser, id: string): Promise<void> {
    await this.get(user, id)
    await this.storage.delete(id)
    if (!(await this.repository.softDeleteForUser(user.id, id, this.now()))) throw trackNotFound()
  }

  async quota(user: CurrentUser): Promise<Quota> {
    const now = this.now()
    const used = await this.repository.countTowardLimit(user.id, startOfDay(now))
    const limit = this.options.dailyLimit
    return { limit, used, remaining: Math.max(0, limit - used), resetsAt: startOfNextDay(now) }
  }

  /**
   * Part of deleting an account: every audio file, then the rows. In this order a failed file
   * delete leaves the rows in place, so the retried account deletion still finds the files.
   */
  async deleteAllForUser(userId: string): Promise<void> {
    const ids = await this.repository.listIdsForUser(userId)
    await Promise.all(ids.map((id) => this.storage.delete(id)))
    await this.repository.deleteAllForUser(userId)
  }
}

const trackNotFound = () => new ApiError(404, 'TRACK_NOT_FOUND', 'No such track')

const sameRequest = (
  track: Track,
  request: Pick<Track, 'prompt' | 'lengthMs' | 'instrumental' | 'modelId'>,
) =>
  track.prompt === request.prompt &&
  track.lengthMs === request.lengthMs &&
  track.instrumental === request.instrumental &&
  track.modelId === request.modelId

const startOfDay = (time: Date) =>
  new Date(Date.UTC(time.getUTCFullYear(), time.getUTCMonth(), time.getUTCDate()))

const startOfNextDay = (time: Date) => new Date(startOfDay(time).getTime() + 24 * 60 * 60 * 1000)
