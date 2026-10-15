import { describe, expect, it } from 'vitest'
import { MockMusicProvider } from '../src/features/tracks/generation/mock-music-provider.js'

const provider = new MockMusicProvider(0)
const request = {
  prompt: 'A bright acoustic song for morning',
  lengthMs: 30000,
  instrumental: false,
  modelId: 'music_v2_5' as const,
}
describe('custom lyrics mock contract', () => {
  it('preserves each supplied word once with exact bounded timing', async () => {
    const song = await provider.compose(
      { ...request, customLyrics: 'Morning light\nA new beginning' },
      new AbortController().signal,
    )
    expect(song.words).toEqual([
      { word: 'Morning', startMs: 4000, endMs: 8250 },
      { word: 'light', startMs: 9000, endMs: 13250 },
      { word: 'A', startMs: 14000, endMs: 18250 },
      { word: 'new', startMs: 19000, endMs: 23250 },
      { word: 'beginning', startMs: 24000, endMs: 28250 },
    ])
    expect(song.durationMs).toBe(30000)
  })
  it('keeps instrumentals without lyrics even if legacy input contains text', async () => {
    const song = await provider.compose(
      { ...request, instrumental: true, customLyrics: 'Do not sing this' },
      new AbortController().signal,
    )
    expect(song.words).toEqual([])
  })
  it('keeps demo lyrics when custom lyrics are omitted', async () => {
    const song = await provider.compose(request, new AbortController().signal)
    expect(song.words[0]).toEqual({ word: 'neon', startMs: 4000, endMs: 4500 })
    expect(song.words.length).toBeGreaterThan(5)
  })
})
