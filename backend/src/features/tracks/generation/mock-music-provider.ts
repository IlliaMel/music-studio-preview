import type { Word } from '../tracks.model.js'
import { abortableDelay } from './abortable-delay.js'
import { demoAudio } from './demo-audio.js'
import {
  type ComposedSong,
  type ComposeRequest,
  type MusicProvider,
  ProviderError,
} from './music-provider.js'

const LYRICS =
  'neon lights are calling through the rain tonight we drive until the city fades away and every street remembers'.split(
    ' ',
  )
const DEFAULT_LENGTH_MS = 30_000

/**
 * Stands in for ElevenLabs when there is no API key: no credits, no network, the same flow.
 * A marker in the prompt plays out a failure, so every error screen of the app can be tried:
 * `[mock:bad_prompt]`, `[mock:credits]`, `[mock:rate_limit]`, `[mock:server]`, `[mock:slow]`.
 */
export class MockMusicProvider implements MusicProvider {
  readonly name = 'mock'

  constructor(private readonly delayMs: number) {}

  async compose(request: ComposeRequest, signal: AbortSignal): Promise<ComposedSong> {
    const scenario = /\[mock:([a-z_]+)\]/.exec(request.prompt)?.[1]
    await abortableDelay(scenario === 'slow' ? this.delayMs * 8 : this.delayMs, signal)

    switch (scenario) {
      case 'bad_prompt':
        throw new ProviderError(
          'INVALID_PROMPT',
          'The prompt names a real artist',
          false,
          'An upbeat synth-pop song with warm female vocals',
        )
      case 'credits':
        throw new ProviderError('QUOTA_EXCEEDED', 'The account has no credits left', false)
      case 'rate_limit':
        throw new ProviderError('RATE_LIMITED', 'Too many requests', true)
      case 'server':
        throw new ProviderError('PROVIDER_UNAVAILABLE', 'The service is unavailable', true)
    }

    const durationMs = request.lengthMs ?? DEFAULT_LENGTH_MS
    return {
      audio: demoAudio(durationMs, (request.prompt.length + (request.variantIndex ?? 0) * 3) % 8),
      title:
        titleFrom(request.prompt) + (request.variantIndex ? ` (${request.variantIndex + 1})` : ''),
      durationMs,
      genres: ['mock'],
      explicit: false,
      words: request.instrumental ? [] : spreadLyrics(durationMs, request.customLyrics),
      songId: null,
    }
  }
}

function titleFrom(prompt: string): string {
  const words = prompt
    .replace(/\[mock:[a-z_]+\]/g, '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 4)
  const title = words.map((word) => word[0]?.toUpperCase() + word.slice(1)).join(' ')
  return title || 'Untitled'
}

function spreadLyrics(durationMs: number, customLyrics?: string): Word[] {
  const customWords = customLyrics?.split(/\s+/).filter(Boolean)
  if (customWords?.length) {
    const introMs = Math.min(4_000, Math.floor(durationMs / 4))
    const stepMs = (durationMs - introMs - 1_000) / customWords.length
    return customWords.map((word, index) => ({
      word,
      startMs: Math.floor(introMs + index * stepMs),
      endMs: Math.floor(introMs + (index + 0.85) * stepMs),
    }))
  }
  const lyrics = LYRICS
  const words: Word[] = []
  for (let startMs = 4_000, index = 0; startMs + 500 < durationMs; startMs += 600, index++) {
    words.push({ word: lyrics[index % lyrics.length] ?? '', startMs, endMs: startMs + 500 })
  }
  return words
}
