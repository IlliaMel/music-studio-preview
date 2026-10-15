// MPEG-1 Layer III, 64 kbit/s, 44.1 kHz, mono. A frame whose side information is all zeros
// carries no audio data, so every decoder plays it as silence.
const FRAME_HEADER = [0xff, 0xfb, 0x50, 0xc4]
const FRAME_BYTES = 208 // floor(144 * 64_000 / 44_100)
const FRAME_MS = (1152 / 44_100) * 1000

/** A valid MP3 of silence, so the mock provider needs no audio files and players still work. */
export function silentMp3(durationMs: number): Uint8Array {
  const frames = Math.max(1, Math.ceil(durationMs / FRAME_MS))
  const audio = new Uint8Array(frames * FRAME_BYTES)
  for (let frame = 0; frame < frames; frame++) {
    audio.set(FRAME_HEADER, frame * FRAME_BYTES)
  }
  return audio
}
