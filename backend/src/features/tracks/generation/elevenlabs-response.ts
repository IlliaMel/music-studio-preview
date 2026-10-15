import { z } from 'zod'
import { isMp3 } from '../../../core/audio/audio-container.js'
import { ProviderError } from './music-provider.js'

export const Plan = z.object({
  chunks: z
    .array(z.object({ duration_ms: z.number().int().min(3000).max(120000) }).passthrough())
    .min(1)
    .max(30),
})
const Metadata = z.object({
  composition_plan: Plan,
  song_metadata: z.object({
    title: z.string().max(1000).nullable().optional(),
    genres: z.array(z.string().max(200)).max(100),
    is_explicit: z.boolean().nullable().optional(),
  }),
  words_timestamps: z
    .array(
      z.object({
        word: z.string().max(1000),
        start_ms: z.number().int().nonnegative(),
        end_ms: z.number().int().nonnegative(),
      }),
    )
    .max(20000)
    .nullable()
    .optional(),
})

export async function boundedBody(response: Response, limit = 32 * 1024 * 1024): Promise<Buffer> {
  const length = Number(response.headers.get('content-length'))
  if (length > limit) {
    await response.body?.cancel()
    throw invalidResponse()
  }
  const reader = response.body?.getReader()
  if (!reader) throw invalidResponse()
  const chunks: Uint8Array[] = []
  let size = 0
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      size += value.length
      if (size > limit) throw invalidResponse()
      chunks.push(value)
    }
    return Buffer.concat(chunks, size)
  } finally {
    await reader.cancel().catch(() => {})
  }
}

export function parseDetailed(contentType: string, bytes: Buffer, songId: string | null) {
  const boundary = /boundary=(?:"([^"\r\n]+)"|([^;\s]+))/i.exec(contentType)
  const token = boundary?.[1] ?? boundary?.[2]
  if (!/^multipart\/mixed(?:;|$)/i.test(contentType) || !token || token.length > 100)
    throw invalidResponse()
  const delimiter = Buffer.from(`--${token}`)
  if (!bytes.subarray(0, delimiter.length).equals(delimiter)) throw invalidResponse()
  let offset = delimiter.length
  let metadata: unknown
  let audio: Buffer | undefined
  let parts = 0
  while (!bytes.subarray(offset, offset + 2).equals(Buffer.from('--'))) {
    if (++parts > 3 || bytes.toString('ascii', offset, offset + 2) !== '\r\n')
      throw invalidResponse()
    offset += 2
    const endHeaders = bytes.indexOf('\r\n\r\n', offset)
    if (endHeaders < offset || endHeaders - offset > 8192) throw invalidResponse()
    const headers = bytes.toString('ascii', offset, endHeaders)
    const type = /^content-type:\s*([^\r\n]+)/im.exec(headers)?.[1]?.trim().toLowerCase()
    const beginning = endHeaders + 4
    const next = bytes.indexOf(Buffer.from(`\r\n--${token}`), beginning)
    if (next < 0) throw invalidResponse()
    const data = bytes.subarray(beginning, next)
    if (type?.startsWith('application/json') && metadata === undefined) {
      if (data.length > 2 * 1024 * 1024) throw invalidResponse()
      try {
        metadata = JSON.parse(data.toString('utf8'))
      } catch {
        throw invalidResponse()
      }
    } else if (
      (type === 'audio/mpeg' || type === 'audio/mp3' || type === 'application/octet-stream') &&
      !audio
    )
      audio = data
    else throw invalidResponse()
    offset = next + 2 + delimiter.length
  }
  const parsed = Metadata.safeParse(metadata)
  if (!parsed.success || !audio?.length || !isMp3(audio)) throw invalidResponse()
  const durationMs = parsed.data.composition_plan.chunks.reduce(
    (sum, chunk) => sum + chunk.duration_ms,
    0,
  )
  if (durationMs < 3000 || durationMs > 600000) throw invalidResponse()
  const words = (parsed.data.words_timestamps ?? []).map((word) => ({
    word: word.word,
    startMs: word.start_ms,
    endMs: word.end_ms,
  }))
  if (
    words.some(
      (word, i) =>
        word.endMs < word.startMs ||
        word.endMs > durationMs + 1000 ||
        (i > 0 && word.startMs < (words[i - 1]?.startMs ?? 0)),
    )
  )
    throw invalidResponse()
  return {
    audio,
    durationMs,
    words,
    title: parsed.data.song_metadata.title ?? null,
    genres: parsed.data.song_metadata.genres,
    explicit: parsed.data.song_metadata.is_explicit ?? false,
    songId,
  }
}

export const invalidResponse = () =>
  new ProviderError(
    'PROVIDER_OUTCOME_UNKNOWN',
    'ElevenLabs returned an incomplete or invalid response; check its request history before retrying',
    false,
  )
