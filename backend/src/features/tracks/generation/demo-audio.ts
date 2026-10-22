import type { ByteRange, StoredFile } from '../../../core/storage/audio-storage.js'
import { arrangementSample, type DemoArrangement } from './demo-arrangements.js'

export function demoAudio(
  durationMs: number,
  seed = 0,
  range?: ByteRange,
  arrangement?: DemoArrangement,
): Uint8Array {
  const sampleRate = 22050
  const samples = Math.floor((durationMs * sampleRate) / 1000)
  const size = 44 + samples * 2
  const start = range?.start ?? 0
  const end = range?.end ?? size - 1
  const header = Buffer.alloc(44)
  const audio = Buffer.alloc(end - start + 1)
  header.write('RIFF', 0)
  header.writeUInt32LE(size - 8, 4)
  header.write('WAVEfmt ', 8)
  header.writeUInt32LE(16, 16)
  header.writeUInt16LE(1, 20)
  header.writeUInt16LE(1, 22)
  header.writeUInt32LE(sampleRate, 24)
  header.writeUInt32LE(sampleRate * 2, 28)
  header.writeUInt16LE(2, 32)
  header.writeUInt16LE(16, 34)
  header.write('data', 36)
  header.writeUInt32LE(samples * 2, 40)
  const notes = [261.63, 329.63, 392, 523.25, 440, 392, 329.63, 293.66]
  if (start < 44) header.copy(audio, 0, start, Math.min(44, end + 1))
  const sampleBytes = Buffer.alloc(2)
  for (
    let index = Math.max(0, Math.floor((start - 44) / 2));
    index < Math.min(samples, Math.ceil((end + 1 - 44) / 2));
    index++
  ) {
    const time = index / sampleRate
    const fade = Math.min(1, time * 5, (durationMs / 1000 - time) * 5)
    let sample: number
    if (arrangement === undefined) {
      const beat = time % 0.5
      const frequency = notes[(Math.floor(time * 2) + seed) % notes.length] ?? 261.63
      const envelope = Math.min(1, beat * 30) * Math.exp(-beat * 5)
      const melody = Math.sin(2 * Math.PI * frequency * time) * 0.19 * envelope
      const bass = Math.sin(2 * Math.PI * 130.815 * time) * 0.08
      sample = melody + bass
    } else {
      sample = arrangementSample(arrangement, index, sampleRate, seed)
    }
    sampleBytes.writeInt16LE(
      Math.round(Math.max(-0.95, Math.min(0.95, sample)) * Math.max(0, fade) * 32767),
    )
    const position = 44 + index * 2
    for (let byte = 0; byte < 2; byte++)
      if (position + byte >= start && position + byte <= end)
        audio[position + byte - start] = sampleBytes[byte] ?? 0
  }
  return audio
}

export function demoAudioFile(
  durationMs: number,
  seed = 0,
  arrangement?: DemoArrangement,
): StoredFile {
  const size = 44 + Math.floor((durationMs * 22050) / 1000) * 2
  return {
    size,
    contentType: 'audio/wav',
    stream(range) {
      let offset = range?.start ?? 0
      const end = range?.end ?? size - 1
      return new ReadableStream({
        pull(controller) {
          if (offset > end) {
            controller.close()
            return
          }
          const next = Math.min(end, offset + 65535)
          controller.enqueue(demoAudio(durationMs, seed, { start: offset, end: next }, arrangement))
          offset = next + 1
        },
      })
    },
  }
}
