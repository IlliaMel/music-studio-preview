import type { Word } from '../tracks.table.js'
import { abortableDelay } from './abortable-delay.js'
import {
  type ComposedSong,
  type ComposeRequest,
  type MusicProvider,
  ProviderError,
} from './music-provider.js'
import { silentMp3 } from './silent-mp3.js'

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
      audio: silentMp3(durationMs),
      title: titleFrom(request.prompt),
      durationMs,
      genres: ['mock'],
      explicit: false,
      words: request.instrumental ? [] : spreadLyrics(durationMs),
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

/** The same lines over and over, one word every 600 ms after a 4-second intro. */
function spreadLyrics(durationMs: number): Word[] {
  const words: Word[] = []
  for (let startMs = 4_000, index = 0; startMs + 500 < durationMs; startMs += 600, index++) {
    words.push({ word: LYRICS[index % LYRICS.length] ?? '', startMs, endMs: startMs + 500 })
  }
  return words
}
