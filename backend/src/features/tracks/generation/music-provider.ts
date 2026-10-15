import type { Word } from '../tracks.model.js'

export const MODEL_IDS = ['music_v2_5', 'music_v2'] as const
export type ModelId = (typeof MODEL_IDS)[number]

export type ComposeRequest = {
  prompt: string
  variantIndex?: number
  quality?: 'auto' | 'deep' | 'fast'
  customLyrics?: string
  referenceUploadId?: string
  remixTrackId?: string
  /** Undefined lets the model choose the length. */
  lengthMs?: number
  instrumental: boolean
  modelId: ModelId
}

export type ComposedSong = {
  /** Encoded audio bytes; the mock returns WAV and a remote provider can return MP3. */
  audio: Uint8Array
  title: string | null
  durationMs: number
  genres: string[]
  explicit: boolean
  /** Empty for an instrumental. */
  words: Word[]
  songId: string | null
}

/**
 * Turns a description into a song. ElevenLabs answers only when the whole song is ready, which
 * takes from half a minute to a few minutes, so this always runs in the background worker.
 */
export interface MusicProvider {
  readonly name: string
  compose(request: ComposeRequest, signal: AbortSignal): Promise<ComposedSong>
}

/** Stable codes the app maps to its own messages. */
export type GenerationErrorCode =
  | 'INVALID_PROMPT'
  | 'QUOTA_EXCEEDED'
  | 'RATE_LIMITED'
  | 'PROVIDER_UNAVAILABLE'
  | 'PROVIDER_AUTH'
  | 'TIMEOUT'
  | 'INTERRUPTED'
  | 'INTERNAL'

/** A failure already translated from the provider's terms into ours. */
export class ProviderError extends Error {
  constructor(
    readonly code: GenerationErrorCode,
    message: string,
    /** Worth trying again in a few seconds: rate limits, a busy or briefly unavailable service. */
    readonly retryable: boolean,
    /** A rewritten prompt the provider would accept, when it rejected ours. */
    readonly promptSuggestion?: string,
  ) {
    super(message)
    this.name = 'ProviderError'
  }
}
