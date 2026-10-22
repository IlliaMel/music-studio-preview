import type { Logger } from '../../../core/logger.js'
import type { AudioStorage } from '../../../core/storage/audio-storage.js'
import { remixSourceIds } from '../tracks.model.js'
import type { TrackFailure, TracksRepository } from '../tracks.repository.js'
import type { Track } from '../tracks.table.js'
import { abortableDelay } from './abortable-delay.js'
import {
  type ComposedSong,
  type ModelId,
  type MusicProvider,
  ProviderError,
} from './music-provider.js'

export type GenerationWorkerOptions = {
  /** How many songs are generated at the same time; ElevenLabs limits this per plan. */
  concurrency: number
  /** Gives up on one song after this long, retries included. */
  timeoutMs: number
  /** Pauses before each retry of a rate-limited or briefly unavailable provider. */
  retryDelaysMs: readonly number[]
  /** How often an idle worker looks for work it was not woken up for. */
  pollIntervalMs: number
}

const SAVE_FAILED: TrackFailure = {
  code: 'INTERNAL',
  message: 'The song was generated but could not be saved',
  promptSuggestion: null,
}

const ABANDONED: TrackFailure = {
  code: 'INTERRUPTED',
  message: 'The server restarted during the generation',
  promptSuggestion: null,
}

/**
 * Generates songs in the background. The queue is the `tracks` table itself: a new track is
 * `queued`, a worker loop claims it and calls the provider, then stores the MP3 and the result.
 * Nothing is kept only in memory, so a restart loses no queued work.
 */
export class GenerationWorker {
  private running = false
  private loops: Promise<void>[] = []
  private readonly sleepers = new Set<() => void>()
  /** Cancels the generations in progress when the server shuts down. */
  private shutdown = new AbortController()
  private reaper?: NodeJS.Timeout

  constructor(
    private readonly deps: {
      repository: TracksRepository
      provider: MusicProvider
      storage: AudioStorage
      log: Logger
      now?: () => Date
    },
    private readonly options: GenerationWorkerOptions,
  ) {}

  start(): void {
    if (this.running) return
    this.running = true
    this.shutdown = new AbortController()
    void this.failAbandoned()
    // A track that has been running longer than any generation can take lost its worker.
    this.reaper = setInterval(() => void this.failAbandoned(), 60_000).unref()
    for (let loop = 0; loop < this.options.concurrency; loop++) {
      this.loops.push(this.loop())
    }
  }

  /** Called when a track is queued, so an idle loop starts on it now rather than at its next poll. */
  wake(): void {
    for (const sleeper of this.sleepers) sleeper()
  }

  /**
   * Stops taking new work and cancels the songs in progress, which go back to the queue: the
   * next server (after a deploy) or the next start generates them again. Waiting instead would
   * outlast the few seconds a platform gives a stopping server.
   */
  async stop(): Promise<void> {
    this.running = false
    clearInterval(this.reaper)
    this.shutdown.abort()
    this.wake()
    await Promise.all(this.loops)
    this.loops = []
  }

  /** Claims and generates one queued track. Returns false when the queue is empty. */
  async runOnce(): Promise<boolean> {
    const track = await this.deps.repository.claimNext(this.now())
    if (!track) return false
    await this.generate(track)
    return true
  }

  private async loop(): Promise<void> {
    while (this.running) {
      const worked = await this.runOnce().catch((error: unknown) => {
        this.deps.log.error({ err: error }, 'generation worker failed')
        return false
      })
      if (!worked && this.running) await this.sleep(this.options.pollIntervalMs)
    }
  }

  private async generate(track: Track): Promise<void> {
    const log = this.deps.log.child({ trackId: track.id })
    if (
      (this.deps.provider.paid && track.providerName !== this.deps.provider.name) ||
      (track.providerName === 'elevenlabs' && this.deps.provider.name !== 'elevenlabs')
    ) {
      await this.recordFailure(
        track,
        {
          code: 'UNSUPPORTED_MODE',
          message:
            'Queued provider differs from active provider; configure the original provider before generating new requests',
          promptSuggestion: null,
        },
        log,
        undefined,
      )
      return
    }
    const startedAt = performance.now()
    const timeout = AbortSignal.timeout(this.options.timeoutMs)
    const signal = AbortSignal.any([timeout, this.shutdown.signal])

    let song: ComposedSong
    try {
      song = await withRetries(
        () =>
          this.deps.provider.compose(
            {
              identity: {
                trackId: track.id,
                userId: track.userId,
                generationId: track.generationId,
              },
              prompt: track.prompt,
              variantIndex: track.variantIndex,
              quality: track.quality as 'auto' | 'deep' | 'fast',
              customLyrics: track.customLyrics ?? undefined,
              referenceUploadId: track.referenceUploadId ?? undefined,
              remixTrackId: remixSourceIds(track)[0],
              remixTrackIds: remixSourceIds(track).length ? remixSourceIds(track) : undefined,
              lengthMs: track.lengthMs ?? undefined,
              instrumental: track.instrumental,
              modelId: track.modelId as ModelId,
            },
            signal,
          ),
        this.deps.provider.paid ? [] : this.options.retryDelaysMs,
        signal,
      )
    } catch (error) {
      if (this.shutdown.signal.aborted && !timeout.aborted && !this.deps.provider.paid) {
        await this.deps.repository.requeue(track.id, this.now()).catch((requeueError) => {
          log.error({ err: requeueError }, 'could not requeue; it will be failed as abandoned')
        })
        log.info('generation cancelled by shutdown, back in the queue')
        return
      }
      await this.recordFailure(
        track,
        this.deps.provider.paid &&
          (!(error instanceof ProviderError) || error.code === 'PROVIDER_OUTCOME_UNKNOWN')
          ? {
              code: 'PROVIDER_OUTCOME_UNKNOWN',
              message:
                'Provider outcome unknown; check ElevenLabs request history before creating another generation',
              promptSuggestion: null,
            }
          : toFailure(error, timeout),
        log,
        error,
      )
      return
    }

    // The song exists and is paid for; from here on a failure is ours, not the provider's.
    try {
      const { audio: _, ...result } = song
      let saved = false
      for (let attempt = 0; ; attempt++) {
        try {
          saved = await this.deps.repository.complete(track.id, result, this.now(), () =>
            this.deps.storage.save(track.id, song.audio),
          )
          break
        } catch (error) {
          if (attempt === 2) throw error
          await abortableDelay(50 * (attempt + 1), this.shutdown.signal)
        }
      }
      // Deleted while it was being generated: the credits are spent, the file is not needed.
      if (!saved) await this.deps.storage.delete(track.id)
      log.info({ durationMs: Math.round(performance.now() - startedAt) }, 'track generated')
    } catch (error) {
      // A paid result or a lost database acknowledgement cannot justify another paid POST.
      // Retain any audio already written so the result can be recovered by track id.
      if (!this.deps.provider.paid) await this.deps.storage.delete(track.id).catch(() => {})
      await this.recordFailure(
        track,
        this.deps.provider.paid
          ? {
              ...SAVE_FAILED,
              code: 'PROVIDER_OUTCOME_UNKNOWN',
              message:
                'The paid song could not be saved; recover the existing result instead of generating again',
            }
          : SAVE_FAILED,
        log,
        error,
      )
    }
  }

  /** If even this write fails, the track stays running and is failed later as abandoned. */
  private async recordFailure(
    track: Track,
    failure: TrackFailure,
    log: Logger,
    error: unknown,
  ): Promise<void> {
    const level = failure.code === 'INTERNAL' ? 'error' : 'warn'
    log[level]({ code: failure.code, err: error }, 'generation failed')
    await this.deps.repository.fail(track.id, failure, this.now()).catch((writeError) => {
      log.error({ err: writeError }, 'could not record the failure')
    })
  }

  private async failAbandoned(): Promise<void> {
    const startedBefore = new Date(this.now().getTime() - this.options.timeoutMs - 60_000)
    try {
      const count = await this.deps.repository.failAbandoned(startedBefore, ABANDONED, this.now())
      if (count > 0) this.deps.log.warn({ count }, 'failed abandoned generations')
    } catch (error) {
      this.deps.log.error({ err: error }, 'could not check for abandoned generations')
    }
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => {
      const wakeUp = () => {
        clearTimeout(timer)
        this.sleepers.delete(wakeUp)
        resolve()
      }
      const timer = setTimeout(wakeUp, ms)
      this.sleepers.add(wakeUp)
    })
  }

  private now(): Date {
    return this.deps.now?.() ?? new Date()
  }
}

/** Retries only what may pass on its own: rate limits and a briefly unavailable service. */
async function withRetries<T>(
  attempt: () => Promise<T>,
  delaysMs: readonly number[],
  signal: AbortSignal,
): Promise<T> {
  for (let retry = 0; ; retry++) {
    try {
      return await attempt()
    } catch (error) {
      const delay = delaysMs[retry]
      const retryable = error instanceof ProviderError && error.retryable
      if (!retryable || delay === undefined || signal.aborted) throw error
      await abortableDelay(delay, signal)
    }
  }
}

function toFailure(error: unknown, timeout: AbortSignal): TrackFailure {
  // A confirmed rejection is stronger evidence than a deadline that elapsed while decoding
  // its response. Preserve it so accounting can release only that definitely unspent variant.
  if (
    error instanceof ProviderError &&
    (error.code === 'QUOTA_EXCEEDED' ||
      error.code === 'PROVIDER_AUTH' ||
      error.code === 'SERVICE_DAILY_LIMIT')
  ) {
    return { code: error.code, message: error.message, promptSuggestion: null }
  }
  if (timeout.aborted) {
    return { code: 'TIMEOUT', message: 'The generation took too long', promptSuggestion: null }
  }
  if (error instanceof ProviderError) {
    return {
      code: error.code,
      message: error.message,
      promptSuggestion: error.promptSuggestion ?? null,
    }
  }
  return { code: 'INTERNAL', message: 'The generation failed unexpectedly', promptSuggestion: null }
}
