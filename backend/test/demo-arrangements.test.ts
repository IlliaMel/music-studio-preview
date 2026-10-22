import { createHash } from 'node:crypto'
import { expect, it } from 'vitest'
import { catalogAudioSettings } from '../src/features/library/catalog-audio.js'
import type { DemoArrangement } from '../src/features/tracks/generation/demo-arrangements.js'
import { demoAudio, demoAudioFile } from '../src/features/tracks/generation/demo-audio.js'

const arrangements: DemoArrangement[] = ['keys', 'pulse', 'waltz', 'ambient']
const digest = (bytes: Uint8Array) => createHash('sha256').update(bytes).digest('hex')

it('produces four audibly populated, distinct arrangements with headroom and deterministic PCM', () => {
  const hashes = new Set<string>()
  for (const arrangement of arrangements) {
    const wave = Buffer.from(demoAudio(4000, 17, undefined, arrangement))
    expect(wave.readUInt32LE(24)).toBe(22050)
    expect(wave.readUInt16LE(22)).toBe(1)
    let peak = 0
    let squares = 0
    let count = 0
    for (let offset = 44; offset < wave.length; offset += 2) {
      const sample = wave.readInt16LE(offset) / 32768
      peak = Math.max(peak, Math.abs(sample))
      squares += sample * sample
      count++
    }
    expect(Math.sqrt(squares / count), arrangement).toBeGreaterThan(0.025)
    expect(peak, arrangement).toBeLessThan(0.9)
    expect(wave.readInt16LE(44), arrangement).toBe(0)
    hashes.add(digest(wave.subarray(44)))
    expect(digest(demoAudio(4000, 17, undefined, arrangement))).toBe(digest(wave))
  }
  expect(hashes.size).toBe(4)
})

it.each(arrangements)(
  'keeps %s PCM byte-exact for seeks and split range downloads',
  async (arrangement) => {
    const full = Buffer.from(demoAudio(3000, 23, undefined, arrangement))
    const file = demoAudioFile(3000, 23, arrangement)
    for (const range of [
      { start: 40, end: 47 },
      { start: 45, end: 4096 },
      { start: 32001, end: 98002 },
      { start: full.length - 3, end: full.length - 1 },
    ]) {
      const actual = Buffer.from(await new Response(file.stream(range)).arrayBuffer())
      expect(actual).toEqual(full.subarray(range.start, range.end + 1))
    }
  },
)

it('uses stable track identity and genres rather than one shared seed for the catalog', () => {
  const first = catalogAudioSettings({ id: 'rainy-keys', genres: ['jazz'] })
  const second = catalogAudioSettings({ id: 'cold-brew', genres: ['jazz'] })
  expect(first).toEqual(catalogAudioSettings({ id: 'rainy-keys', genres: ['jazz'] }))
  expect(first.arrangement).toBe('keys')
  expect(second.arrangement).toBe('keys')
  expect(first.seed).not.toBe(second.seed)
  expect(digest(demoAudio(2000, first.seed, undefined, first.arrangement))).not.toBe(
    digest(demoAudio(2000, second.seed, undefined, second.arrangement)),
  )
  expect(catalogAudioSettings({ id: 'neon-steps', genres: ['electronic'] }).arrangement).toBe(
    'pulse',
  )
  expect(catalogAudioSettings({ id: 'cedar-waltz', genres: ['acoustic'] }).arrangement).toBe(
    'waltz',
  )
  expect(catalogAudioSettings({ id: 'tide-pool', genres: ['ambient'] }).arrangement).toBe('ambient')
})
