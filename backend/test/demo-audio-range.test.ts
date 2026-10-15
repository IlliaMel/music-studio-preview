import { expect, it } from 'vitest'
import { demoAudio, demoAudioFile } from '../src/features/tracks/generation/demo-audio.js'

it('serves odd and header-crossing ranges identically to the full WAV without synthesizing its full duration', async () => {
  const full = Buffer.from(demoAudio(3000, 2))
  for (const range of [
    { start: 0, end: 0 },
    { start: 40, end: 47 },
    { start: 45, end: 76 },
    { start: full.length - 3, end: full.length - 1 },
  ]) {
    const streamed = Buffer.from(
      await new Response(demoAudioFile(3000, 2).stream(range)).arrayBuffer(),
    )
    expect(streamed).toEqual(full.subarray(range.start, range.end + 1))
  }
  const file = demoAudioFile(260000)
  expect(file.size).toBe(11466044)
  expect(Buffer.from(await new Response(file.stream({ start: 0, end: 0 })).arrayBuffer())).toEqual(
    Buffer.from('R'),
  )
})
