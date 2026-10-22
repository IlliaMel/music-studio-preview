import { createHash } from 'node:crypto'
import { z } from 'zod'
import { validAudio } from '../../../core/audio/audio-container.js'
import { remixSourceIds, sungWords, type Word } from '../tracks.model.js'
import { boundedBody, invalidResponse, Plan, parseDetailed } from './elevenlabs-response.js'
import {
  type ComposeRequest,
  type MusicProvider,
  type ProviderDiagnostic,
  ProviderError,
} from './music-provider.js'

/** What a remix changes: the original's description, genres and sung words. */
export type RemixOriginal = { prompt: string; genres: string[]; words: Word[] }

export type MusicReference = {
  key: string
  songId?: string
  durationMs?: number
  audio?: Buffer
  contentType?: string
  attempted?: boolean
  attemptId?: string
  original?: RemixOriginal
}

const PlannedChunks = z.object({
  chunks: z
    .array(
      z.object({
        text: z.string().max(10000).nullable().optional(),
        duration_ms: z.number().int().min(3000).max(120000),
        positive_styles: z.array(z.string().max(300)).max(50).nullable().optional(),
        negative_styles: z.array(z.string().max(300)).max(50).nullable().optional(),
        context_adherence: z.enum(['low', 'medium', 'high']).nullable().optional(),
      }),
    )
    .min(1)
    .max(30),
})
type RemixPlan = z.infer<typeof PlannedChunks>['chunks']
export type ReferenceAccess = {
  resolve(request: ComposeRequest): Promise<MusicReference>
  claim(reference: MusicReference): Promise<boolean>
  save(reference: MusicReference, songId: string, durationMs: number): Promise<void>
  rejected?(reference: MusicReference): Promise<void>
}

export class ElevenLabsMusicProvider implements MusicProvider {
  readonly name = 'elevenlabs'
  readonly paid = true
  get capabilities() {
    return { references: this.options.references }
  }
  private readonly uploads = new Map<string, Promise<{ songId: string; durationMs: number }>>()
  constructor(
    private readonly options: {
      apiKey: string
      references: boolean
      access: ReferenceAccess
      fetch?: typeof fetch
      baseUrl?: string
    },
  ) {}

  validate(request: ComposeRequest): void {
    const remixIds = remixSourceIds(request)
    const remixTrackId = remixIds[0]
    if (remixIds.length > 1)
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'The current live adapter supports one remix original',
        false,
      )
    if (request.quality && request.quality !== 'auto')
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'ElevenLabs has no documented Deep/Fast quality control; choose Auto',
        false,
      )
    if (request.customLyrics && request.instrumental)
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'Custom lyrics cannot be combined with instrumental mode',
        false,
      )
    if ((request.referenceUploadId || remixTrackId) && !this.options.references)
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'Audio references are turned off on this server (ELEVENLABS_ENABLE_REFERENCES=false)',
        false,
      )
    if (request.referenceUploadId && remixTrackId)
      throw new ProviderError('UNSUPPORTED_MODE', 'Choose one reference source', false)
    if (remixTrackId && !/^[0-9a-f-]{36}$/i.test(remixTrackId))
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'Mock catalog tracks cannot be remixed with the live provider',
        false,
      )
    if (request.customLyrics || request.referenceUploadId || remixTrackId) buildChunks(request)
    if (request.customLyrics?.split('\n').some((line) => [...line].length > 200))
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'ElevenLabs lyrics allow at most 200 characters per line',
        false,
      )
    if (
      request.customLyrics &&
      [...request.customLyrics.matchAll(/^[ \t]*\[([^\]\r\n]*)\]/gm)].some(
        (match) => [...(match[1] ?? '')].length < 1 || [...(match[1] ?? '')].length > 100,
      )
    )
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'Section names must contain 1 to 100 characters',
        false,
      )
  }

  async compose(request: ComposeRequest, signal: AbortSignal) {
    request = { ...request, remixTrackId: remixSourceIds(request)[0] }
    this.validate(request)
    signal.throwIfAborted()
    let body: Record<string, unknown> = {
      model_id: request.modelId,
      with_timestamps: true,
      store_for_inpainting: this.options.references,
    }
    if (request.customLyrics || request.referenceUploadId || request.remixTrackId) {
      const reference =
        request.referenceUploadId || request.remixTrackId
          ? await this.options.access.resolve(request)
          : undefined
      const original = request.remixTrackId ? reference?.original : undefined
      const plan = original ? await this.planRemix(request, original, signal) : undefined
      const chunks = plan
        ? plannedChunks(request, original, plan)
        : buildChunks({
            ...request,
            customLyrics:
              request.customLyrics ??
              (request.instrumental
                ? undefined
                : originalLines(original, request.lengthMs ?? 120000).join('\n') || undefined),
          })
      if (reference) {
        const source =
          reference.songId && reference.durationMs
            ? { songId: reference.songId, durationMs: reference.durationMs }
            : await this.uploadOnce(reference, signal)
        const first = chunks[0]
        if (!first) throw invalidResponse()
        first.conditioning_ref = {
          song_id: source.songId,
          range: { start_ms: 0, end_ms: Math.min(source.durationMs, 30000) },
        }
        // A remix asks for a change, so the original only guides it; a tool keeps the recorded melody.
        first.condition_strength = original ? 'low' : 'medium'
      }
      body = { ...body, composition_plan: { chunks }, seed: stableSeed(request) }
    } else
      body = {
        ...body,
        prompt: request.prompt,
        music_length_ms: request.lengthMs,
        force_instrumental: request.instrumental,
      }
    const response = await this.post(
      '/v1/music/detailed?output_format=mp3_44100_128',
      JSON.stringify(body),
      signal,
      'application/json',
    )
    try {
      return parseDetailed(
        response.headers.get('content-type') ?? '',
        await boundedBody(response),
        this.options.references ? response.headers.get('song-id') : null,
      )
    } catch (error) {
      if (error instanceof ProviderError) throw error
      throw invalidResponse()
    }
  }

  /**
   * Plans the remix through the free plan endpoint: sections with the English style tags that "the
   * original plus the requested change" needs, since a request such as "make it more rocky" is not a
   * style on its own. If planning fails, the remix still composes, with the request as its style.
   */
  private async planRemix(
    request: ComposeRequest,
    original: RemixOriginal,
    signal: AbortSignal,
  ): Promise<RemixPlan | undefined> {
    const genres = original.genres.length ? ` (${original.genres.join(', ')})` : ''
    const prompt = `A remix of an existing song: ${original.prompt}${genres}. Requested change: ${request.prompt}`
    try {
      const response = await this.post(
        '/v1/music/plan',
        JSON.stringify({
          prompt: prompt.slice(0, 2000),
          music_length_ms: request.lengthMs ?? 120000,
          model_id: request.modelId,
        }),
        AbortSignal.any([signal, AbortSignal.timeout(20000)]),
        'application/json',
      )
      const { chunks } = PlannedChunks.parse(
        JSON.parse((await boundedBody(response, 1024 * 1024)).toString('utf8')),
      )
      return chunks.some((chunk) => chunk.positive_styles?.length) ? chunks : undefined
    } catch {
      signal.throwIfAborted()
      return undefined
    }
  }

  private async uploadOnce(reference: MusicReference, signal: AbortSignal) {
    const existing = this.uploads.get(reference.key)
    if (existing) return existing
    const pending = this.upload(reference, signal)
    this.uploads.set(reference.key, pending)
    try {
      return await pending
    } finally {
      this.uploads.delete(reference.key)
    }
  }

  private async upload(reference: MusicReference, signal: AbortSignal) {
    const audio = reference.audio
    if (!audio || !reference.contentType || !validAudio(audio, reference.contentType))
      throw new ProviderError(
        'INVALID_REFERENCE',
        'Reference container does not match a supported WAV, MP3 or MP4 audio file',
        false,
      )
    if (reference.attempted || !(await this.options.access.claim(reference)))
      throw new ProviderError(
        'PROVIDER_OUTCOME_UNKNOWN',
        'Reference upload already attempted; check ElevenLabs history before uploading another copy',
        false,
      )
    const form = new FormData()
    const extension =
      reference.contentType === 'audio/mpeg'
        ? 'mp3'
        : reference.contentType === 'audio/mp4'
          ? 'm4a'
          : 'wav'
    form.set(
      'file',
      new Blob([new Uint8Array(audio)], { type: reference.contentType }),
      `reference.${extension}`,
    )
    form.set('extract_composition_plan', 'music_v2_5')
    let response: Response
    try {
      response = await this.post('/v1/music/upload', form, signal)
    } catch (error) {
      if (
        error instanceof ProviderError &&
        (error.code === 'PROVIDER_AUTH' || error.code === 'QUOTA_EXCEEDED')
      ) {
        await this.options.access.rejected?.(reference)
      }
      throw error
    }
    let parsed: unknown
    try {
      parsed = JSON.parse((await boundedBody(response, 2 * 1024 * 1024)).toString('utf8'))
    } catch {
      throw invalidResponse()
    }
    const result = z
      .object({ song_id: z.string().min(1).max(200), composition_plan: Plan })
      .safeParse(parsed)
    if (!result.success) throw invalidResponse()
    const durationMs = result.data.composition_plan.chunks.reduce(
      (sum, chunk) => sum + chunk.duration_ms,
      0,
    )
    if (durationMs < 3000 || durationMs > 600000)
      throw new ProviderError(
        'INVALID_REFERENCE',
        'Reference duration must be between 3 seconds and 10 minutes',
        false,
      )
    await this.options.access.save(reference, result.data.song_id, durationMs)
    return { songId: result.data.song_id, durationMs }
  }

  private async post(
    path: string,
    body: string | FormData,
    signal: AbortSignal,
    contentType?: string,
  ): Promise<Response> {
    let response: Response
    try {
      response = await (this.options.fetch ?? fetch)(
        new URL(path, this.options.baseUrl ?? 'https://api.elevenlabs.io'),
        {
          method: 'POST',
          headers: {
            'xi-api-key': this.options.apiKey,
            ...(contentType ? { 'Content-Type': contentType } : {}),
          },
          body,
          signal,
          redirect: 'error',
        },
      )
    } catch {
      throw new ProviderError(
        'PROVIDER_OUTCOME_UNKNOWN',
        'ElevenLabs connection ended without a confirmed result; check request history before retrying',
        false,
      )
    }
    if (response.ok) return response
    let providerStatus: string | undefined
    let suggestion: string | undefined
    let field: string | undefined
    try {
      const errorBody: unknown = JSON.parse((await boundedBody(response, 65536)).toString('utf8'))
      const detail = z
        .object({
          detail: z.object({
            status: z.string(),
            data: z.object({ prompt_suggestion: z.string().max(2000).optional() }).optional(),
          }),
        })
        .safeParse(errorBody)
      if (detail.success) {
        providerStatus = detail.data.detail.status
        if (providerStatus === 'bad_prompt') suggestion = detail.data.detail.data?.prompt_suggestion
      }
      // A rejected request body lists its invalid fields instead: keep the first one's path and kind.
      const invalid = z
        .object({
          detail: z.array(
            z.object({ loc: z.array(z.union([z.string(), z.number()])), type: z.string() }),
          ),
        })
        .safeParse(errorBody)
      const first = invalid.success ? invalid.data.detail[0] : undefined
      if (first) field = `${first.loc.join('.')} ${first.type}`
    } catch {}
    const diagnostic: ProviderDiagnostic = {
      step: path.startsWith('/v1/music/upload') ? 'upload' : 'compose',
      httpStatus: response.status,
      ...(providerStatus ? { providerStatus } : {}),
      ...(field ? { field } : {}),
    }
    if (
      providerStatus === 'quota_exceeded' ||
      providerStatus === 'insufficient_credits' ||
      response.status === 402
    )
      throw new ProviderError(
        'QUOTA_EXCEEDED',
        'ElevenLabs account has insufficient credits',
        false,
        undefined,
        diagnostic,
      )
    if (response.status === 401 || response.status === 403)
      throw new ProviderError(
        'PROVIDER_AUTH',
        'ElevenLabs key or feature entitlement was rejected',
        false,
        undefined,
        diagnostic,
      )
    if (response.status === 422 || response.status === 400)
      throw new ProviderError(
        'INVALID_PROMPT',
        'ElevenLabs rejected the prompt or composition plan; review its content and supported limits',
        false,
        suggestion,
        diagnostic,
      )
    if (response.status === 429)
      throw new ProviderError(
        'RATE_LIMITED',
        'ElevenLabs rate limit reached; submit a new request later',
        false,
        undefined,
        diagnostic,
      )
    throw new ProviderError(
      'PROVIDER_OUTCOME_UNKNOWN',
      'ElevenLabs did not confirm the result; check request history before retrying',
      false,
      undefined,
      diagnostic,
    )
  }
}

export { validAudio } from '../../../core/audio/audio-container.js'

function stableSeed(request: ComposeRequest): number {
  return (
    createHash('sha256')
      .update(request.identity?.trackId ?? JSON.stringify(request))
      .digest()
      .readUInt32BE(0) & 0x7fffffff
  )
}
/** A vocal remix sings the original's words: the lines sung in the part that fits its length. */
function originalLines(original: RemixOriginal | undefined, lengthMs: number): string[] {
  const words = original ? sungWords(original.words) : []
  if (!words.length) return []
  return lyricLines(words, (words[0]?.startMs ?? 0) + lengthMs * 0.8).slice(0, 900)
}

/**
 * The planned sections, in order, with their own styles. Their lyrics become the remix's: the
 * user's lyrics or the original's words, shared out by section length. Only an original without
 * words keeps the planned lyrics.
 */
function plannedChunks(
  request: ComposeRequest,
  original: RemixOriginal | undefined,
  plan: RemixPlan,
): Record<string, unknown>[] {
  const totalMs = plan.reduce((sum, chunk) => sum + chunk.duration_ms, 0)
  const lyrics = request.instrumental
    ? []
    : (request.customLyrics?.split('\n') ?? originalLines(original, totalMs))
  let taken = 0
  let elapsedMs = 0
  return plan.map((chunk, index) => {
    elapsedMs += chunk.duration_ms
    const until =
      index === plan.length - 1 ? lyrics.length : Math.round((lyrics.length * elapsedMs) / totalMs)
    const lines = lyrics.slice(taken, until).slice(0, 29)
    taken = until
    const label = /^\[[^\]\r\n]{1,100}\]$/.exec(chunk.text?.split('\n')[0]?.trim() ?? '')?.[0]
    const text = request.instrumental
      ? '{instrumental}'
      : lyrics.length
        ? [label, ...(lines.length ? lines : ['{instrumental}'])].filter(Boolean).join('\n')
        : chunk.text?.trim() || '{generate vocals}'
    const negative = chunk.negative_styles ?? []
    return {
      text,
      duration_ms: chunk.duration_ms,
      positive_styles: chunk.positive_styles?.length ? chunk.positive_styles : [request.prompt],
      negative_styles: request.instrumental ? [...negative.slice(0, 49), 'vocals'] : negative,
      ...(chunk.context_adherence ? { context_adherence: chunk.context_adherence } : {}),
    }
  })
}

/** Timed words carry no line breaks: a pause, or a capital after a few words, starts a new line. */
function lyricLines(words: Word[], untilMs: number): string[] {
  const lines: string[] = []
  let line: string[] = []
  let lastEndMs = 0
  for (const { word, startMs, endMs } of words) {
    if (startMs >= untilMs) break
    const text = word.trim()
    const breaks =
      startMs - lastEndMs >= 800 ||
      line.length >= 12 ||
      (line.length >= 4 && /^\p{Lu}/u.test(text)) ||
      [...line, text].join(' ').length > 150
    if (line.length && breaks) {
      lines.push(line.join(' '))
      line = []
    }
    if (text) line.push(text)
    lastEndMs = endMs
  }
  if (line.length) lines.push(line.join(' '))
  return lines
}

function buildChunks(request: ComposeRequest): Record<string, unknown>[] {
  const lines = request.customLyrics?.split('\n') ?? []
  const duration = request.lengthMs ?? 120000
  const count = Math.max(Math.ceil(duration / 120000), Math.ceil(lines.length / 30), 1)
  if (count > 30 || duration / count < 3000)
    throw new ProviderError(
      'UNSUPPORTED_MODE',
      'Lyrics and duration exceed ElevenLabs composition limits',
      false,
    )
  return Array.from({ length: count }, (_, index) => ({
    text:
      lines
        .slice(
          Math.floor((index * lines.length) / count),
          Math.floor(((index + 1) * lines.length) / count),
        )
        .join('\n') || (request.instrumental ? '{instrumental}' : '{generate vocals}'),
    duration_ms:
      Math.floor(((index + 1) * duration) / count) - Math.floor((index * duration) / count),
    positive_styles: [request.prompt],
    negative_styles: request.instrumental ? ['vocals'] : [],
  }))
}
