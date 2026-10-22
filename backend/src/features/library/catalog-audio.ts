import type { DemoArrangement } from '../tracks/generation/demo-arrangements.js'

/** Each track keeps a repeatable voice, tempo and motif across playback, export and HTTP seeks. */
export function catalogAudioSettings(track: { id: string; genres: readonly string[] }): {
  seed: number
  arrangement: DemoArrangement
} {
  let seed = 0x811c9dc5
  for (const character of track.id)
    seed = Math.imul(seed ^ character.charCodeAt(0), 0x01000193) >>> 0
  const genres = track.genres
  const arrangement = genres.includes('ambient')
    ? 'ambient'
    : genres.includes('electronic') || genres.includes('rock')
      ? 'pulse'
      : genres.includes('acoustic') || genres.includes('folk')
        ? 'waltz'
        : 'keys'
  return { seed, arrangement }
}
