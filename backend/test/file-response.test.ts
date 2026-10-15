import { describe, expect, it } from 'vitest'
import { parseRange } from '../src/core/http/file-response.js'
import { silentMp3 } from '../src/features/tracks/generation/silent-mp3.js'

describe('parseRange', () => {
  it.each([
    ['bytes=0-99', { start: 0, end: 99 }],
    ['bytes=900-', { start: 900, end: 999 }],
    ['bytes=-100', { start: 900, end: 999 }],
    ['bytes=950-5000', { start: 950, end: 999 }],
    ['bytes=-5000', { start: 0, end: 999 }],
  ])('reads %s', (header, range) => {
    expect(parseRange(header, 1_000)).toEqual(range)
  })

  it.each(['bytes=1000-', 'bytes=500-400', 'bytes=-0'])('refuses %s', (header) => {
    expect(parseRange(header, 1_000)).toBe('unsatisfiable')
  })

  it.each([undefined, 'bytes=0-1,5-6', 'items=0-5', 'bytes=-'])(
    'sends the whole file for %s',
    (header) => {
      expect(parseRange(header, 1_000)).toBeUndefined()
    },
  )
})

describe('silentMp3', () => {
  it('is whole MPEG frames covering the length asked for', () => {
    const audio = silentMp3(1_000)

    // 1 s at 26.1 ms per frame is 39 frames of 208 bytes, each starting with a frame header.
    expect(audio.length).toBe(39 * 208)
    for (let offset = 0; offset < audio.length; offset += 208) {
      expect([audio[offset], audio[offset + 1]]).toEqual([0xff, 0xfb])
    }
  })
})
