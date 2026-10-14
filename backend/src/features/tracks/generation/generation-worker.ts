import type { Logger } from '../../../core/logger.js'
import type { AudioStorage } from '../../../core/storage/audio-storage.js'
import type { TrackFailure, TracksRepository } from '../tracks.repository.js'
import type { Track } from '../tracks.table.js'
import { abortableDelay } from './abortable-delay.js'
import { type ModelId, type MusicProvider, ProviderError } from './music-provider.js'

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

  /** Stops taking new work and waits for the songs in progress to finish or time out. */
  async stop(): Promise<void> {
    this.running = false
    clearInterval(this.reaper)
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
    const startedAt = performance.now()
    const signal = AbortSignal.timeout(this.options.timeoutMs)
    try {
      const song = await withRetries(
        () =>
          this.deps.provider.compose(
            {
              prompt: track.prompt,
              lengthMs: track.lengthMs ?? undefined,
              instrumental: track.instrumental,
              modelId: track.modelId as ModelId,
            },
            signal,
          ),
        this.options.retryDelaysMs,
        signal,
      )
      await this.deps.storage.save(track.id, song.audio)
      const { audio: _, ...result } = song
      const saved = await this.deps.repository.complete(track.id, result, this.now())
      // Deleted while it was being generated: the credits are spent, the file is not needed.
      if (!saved) await this.deps.storage.delete(track.id)
      log.info({ durationMs: Math.round(performance.now() - startedAt) }, 'track generated')
    } catch (error) {
      const failure = toFailure(error, signal)
      await this.deps.repository.fail(track.id, failure, this.now())
      const level = failure.code === 'INTERNAL' ? 'error' : 'warn'
      log[level]({ code: failure.code, err: error }, 'generation failed')
    }
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

function toFailure(error: unknown, signal: AbortSignal): TrackFailure {
  if (signal.aborted) {
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
