/** Original, deterministic demo arrangements. No samples or external music service are used. */
export type DemoArrangement = 'keys' | 'pulse' | 'waltz' | 'ambient'

const frequencies = Array.from({ length: 128 }, (_, note) => 440 * 2 ** ((note - 69) / 12))
const keys = [64, 67, 71, 74, 71, 67, 69, 66, 64, 71, 76, 74, 69, 67, 66, 62]
const pulse = [62, 69, 65, 72, 74, 69, 65, 60, 62, 65, 69, 77, 74, 72, 69, 65]
const waltz = [
  64, 67, 72, 71, 67, 64, 65, 69, 72, 74, 72, 69, 67, 71, 74, 76, 74, 71, 69, 72, 76, 74, 72, 69,
]
const ambient = [64, 71, 74, 67, 76, 74, 69, 62]
const transpose = [-3, -2, 0, 2, 4]
const tau = Math.PI * 2

const sine = (note: number, age: number): number => Math.sin(tau * (frequencies[note] ?? 440) * age)
const envelope = (age: number, attack: number, decay: number): number =>
  Math.min(1, age / attack) * Math.exp(-age * decay)
const gate = (age: number, length: number, release = 0.025): number =>
  Math.min(1, Math.max(0, length - age) / release)

function noise(index: number, seed: number): number {
  let value = Math.imul(index ^ seed, 0x45d9f3b)
  value = Math.imul(value ^ (value >>> 16), 0x45d9f3b)
  return ((value ^ (value >>> 16)) >>> 0) / 0x7fffffff - 1
}

function noteAt(motif: readonly number[], step: number, seed: number): number {
  return (
    (motif[(step + (seed % motif.length)) % motif.length] ?? 64) +
    (transpose[seed % transpose.length] ?? 0)
  )
}

/** Absolute sample position makes independently requested HTTP ranges exactly reproducible. */
export function arrangementSample(
  arrangement: DemoArrangement,
  sample: number,
  sampleRate: number,
  seed: number,
): number {
  const time = sample / sampleRate
  const shift = transpose[seed % transpose.length] ?? 0
  if (arrangement === 'keys') {
    const beat = 60 / 82
    const step = Math.floor(time / (beat / 2))
    const age = time % (beat / 2)
    const note = noteAt(keys, step, seed)
    const root = ([40, 36, 45, 38][Math.floor(time / (beat * 4)) % 4] ?? 40) + shift
    const piano =
      (sine(note, age) * 0.8 + sine(note + 12, age) * 0.15 + sine(note + 19, age) * 0.05) *
      envelope(age, 0.012, 5)
    const bass = sine(root, time % beat) * envelope(time % beat, 0.025, 2)
    const chordAge = time % (beat * 4)
    const chord =
      (sine(root + 24, chordAge) + sine(root + 31, chordAge)) * envelope(chordAge, 0.12, 0.8)
    return (
      piano * 0.22 * gate(age, beat / 2) +
      bass * 0.095 * gate(time % beat, beat) +
      chord * 0.025 * gate(chordAge, beat * 4)
    )
  }
  if (arrangement === 'pulse') {
    const beat = 60 / 124
    const step = Math.floor(time / (beat / 2))
    const age = time % (beat / 2)
    const beatAge = time % beat
    const note = noteAt(pulse, step, seed)
    const root = ([38, 34, 41, 36][Math.floor(time / (beat * 4)) % 4] ?? 38) + shift
    const synth =
      (sine(note, age) * 0.72 + sine(note + 12, age) * 0.2 + sine(note + 24, age) * 0.08) *
      envelope(age, 0.004, 9)
    const bass = sine(root, beatAge) * envelope(beatAge, 0.008, 5)
    const kick =
      Math.sin(tau * (45 * beatAge + 6 * (1 - Math.exp(-beatAge * 20)))) * Math.exp(-beatAge * 18)
    const hat = noise(sample, seed) * Math.exp(-age * 75)
    const snare =
      Math.floor(time / beat) % 2 === 1 ? noise(sample, seed + 1) * Math.exp(-beatAge * 28) : 0
    return (
      synth * 0.2 * gate(age, beat / 2) +
      bass * 0.11 * gate(beatAge, beat) +
      kick * 0.16 +
      hat * 0.035 +
      snare * 0.08
    )
  }
  if (arrangement === 'waltz') {
    const beat = 60 / 102
    const step = Math.floor(time / (beat / 2))
    const age = time % (beat / 2)
    const barAge = time % (beat * 3)
    const beatAge = time % beat
    const bar = Math.floor(time / (beat * 3)) % 4
    const root = ([48, 53, 55, 57][bar] ?? 48) + shift
    const note = noteAt(waltz, step, seed)
    const pluck =
      (sine(note, age) + sine(note + 12, age) * 0.28 + sine(note + 19, age) * 0.11) *
      envelope(age, 0.006, 7)
    const bass = sine(root - 12, barAge) * envelope(barAge, 0.012, 4)
    const chord =
      Math.floor(time / beat) % 3 === 0
        ? 0
        : (sine(root + 12, beatAge) +
            sine(root + (bar === 3 ? 15 : 16), beatAge) +
            sine(root + 19, beatAge)) *
          envelope(beatAge, 0.01, 9)
    return pluck * 0.17 * gate(age, beat / 2) + bass * 0.12 + chord * 0.035 * gate(beatAge, beat)
  }
  const beat = 60 / 56
  const stepLength = beat * 2
  const age = time % stepLength
  const note = noteAt(ambient, Math.floor(time / stepLength), seed)
  const root = ([40, 36, 43, 38][Math.floor(time / (beat * 8)) % 4] ?? 40) + shift
  const padAge = time % (beat * 8)
  const padEnvelope = Math.sin((Math.PI * padAge) / (beat * 8)) ** 2
  const pad =
    (sine(root, padAge) + sine(root + 7, padAge) * 0.6 + sine(root + 19, padAge) * 0.35) *
    padEnvelope
  const bell = (sine(note, age) + sine(note + 12, age) * 0.3) * envelope(age, 0.22, 0.75)
  const echo = age > 0.4 ? sine(note, age - 0.4) * envelope(age - 0.4, 0.12, 1.1) : 0
  return pad * 0.075 + (bell * 0.17 + echo * 0.045) * gate(age, stepLength, 0.2)
}
