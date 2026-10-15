import { createHash } from 'node:crypto'
import { z } from 'zod'
import { boundedBody, invalidResponse, isMp3, Plan, parseDetailed } from './elevenlabs-response.js'
import { type ComposeRequest, type MusicProvider, ProviderError } from './music-provider.js'

export type MusicReference = {
  key: string
  songId?: string
  durationMs?: number
  audio?: Buffer
  contentType?: string
  attempted?: boolean
}
export type ReferenceAccess = {
  resolve(request: ComposeRequest): Promise<MusicReference>
  claim(reference: MusicReference): Promise<boolean>
  save(reference: MusicReference, songId: string, durationMs: number): Promise<void>
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
    if ((request.referenceUploadId || request.remixTrackId) && !this.options.references)
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'Audio references require ElevenLabs enterprise inpainting access and ELEVENLABS_ENABLE_REFERENCES=true',
        false,
      )
    if (request.referenceUploadId && request.remixTrackId)
      throw new ProviderError('UNSUPPORTED_MODE', 'Choose one reference source', false)
    if (request.remixTrackId && !/^[0-9a-f-]{36}$/i.test(request.remixTrackId))
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'Mock catalog tracks cannot be remixed with the live provider',
        false,
      )
    if (request.customLyrics || request.referenceUploadId || request.remixTrackId)
      buildChunks(request)
    if (request.customLyrics?.split('\n').some((line) => line.length > 200))
      throw new ProviderError(
        'UNSUPPORTED_MODE',
        'ElevenLabs lyrics allow at most 200 characters per line',
        false,
      )
  }

  async compose(request: ComposeRequest, signal: AbortSignal) {
    this.validate(request)
    signal.throwIfAborted()
    let body: Record<string, unknown> = {
      model_id: request.modelId,
      with_timestamps: true,
      store_for_inpainting: this.options.references,
    }
    if (request.customLyrics || request.referenceUploadId || request.remixTrackId) {
      const chunks = buildChunks(request)
      if (request.referenceUploadId || request.remixTrackId) {
        const reference = await this.options.access.resolve(request)
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
        first.condition_strength = 'medium'
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
    const response = await this.post('/v1/music/upload', form, signal)
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
    } catch {}
    if (
      providerStatus === 'quota_exceeded' ||
      providerStatus === 'insufficient_credits' ||
      response.status === 402
    )
      throw new ProviderError(
        'QUOTA_EXCEEDED',
        'ElevenLabs account has insufficient credits',
        false,
      )
    if (response.status === 401 || response.status === 403)
      throw new ProviderError(
        'PROVIDER_AUTH',
        'ElevenLabs key or feature entitlement was rejected',
        false,
      )
    if (response.status === 422 || response.status === 400)
      throw new ProviderError(
        'INVALID_PROMPT',
        'ElevenLabs rejected the prompt or composition plan; review its content and supported limits',
        false,
        suggestion,
      )
    if (response.status === 429)
      throw new ProviderError(
        'RATE_LIMITED',
        'ElevenLabs rate limit reached; submit a new request later',
        false,
      )
    throw new ProviderError(
      'PROVIDER_OUTCOME_UNKNOWN',
      'ElevenLabs did not confirm the result; check request history before retrying',
      false,
    )
  }
}

export function validAudio(data: Buffer, type: string): boolean {
  if (data.length < 12 || data.length > 20 * 1024 * 1024) return false
  if (type === 'audio/mpeg') return isMp3(data)
  if (type === 'audio/mp4')
    return data.toString('ascii', 4, 8) === 'ftyp' && data.includes(Buffer.from('mdat'))
  return (
    (type === 'audio/wav' || type === 'audio/x-wav') &&
    data.toString('ascii', 0, 4) === 'RIFF' &&
    data.toString('ascii', 8, 12) === 'WAVE' &&
    data.includes(Buffer.from('data'))
  )
}

function stableSeed(request: ComposeRequest): number {
  return (
    createHash('sha256')
      .update(request.identity?.trackId ?? JSON.stringify(request))
      .digest()
      .readUInt32BE(0) & 0x7fffffff
  )
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
