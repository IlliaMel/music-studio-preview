export function demoAudio(durationMs: number, seed = 0): Uint8Array {
  const sampleRate = 22050
  const samples = Math.floor((durationMs * sampleRate) / 1000)
  const audio = Buffer.alloc(44 + samples * 2)
  audio.write('RIFF', 0)
  audio.writeUInt32LE(audio.length - 8, 4)
  audio.write('WAVEfmt ', 8)
  audio.writeUInt32LE(16, 16)
  audio.writeUInt16LE(1, 20)
  audio.writeUInt16LE(1, 22)
  audio.writeUInt32LE(sampleRate, 24)
  audio.writeUInt32LE(sampleRate * 2, 28)
  audio.writeUInt16LE(2, 32)
  audio.writeUInt16LE(16, 34)
  audio.write('data', 36)
  audio.writeUInt32LE(samples * 2, 40)
  const notes = [261.63, 329.63, 392, 523.25, 440, 392, 329.63, 293.66]
  for (let index = 0; index < samples; index++) {
    const time = index / sampleRate
    const beat = time % 0.5
    const frequency = notes[(Math.floor(time * 2) + seed) % notes.length] ?? 261.63
    const envelope = Math.min(1, beat * 30) * Math.exp(-beat * 5)
    const fade = Math.min(1, time * 5, (durationMs / 1000 - time) * 5)
    const melody = Math.sin(2 * Math.PI * frequency * time) * 0.19 * envelope
    const bass = Math.sin(2 * Math.PI * 130.815 * time) * 0.08
    audio.writeInt16LE(Math.round((melody + bass) * Math.max(0, fade) * 32767), 44 + index * 2)
  }
  return audio
}
