import { type Context, Hono } from 'hono'
import { z } from 'zod'
import { requireOnboarding } from '../../core/auth/authenticate.js'
import type { AppEnv } from '../../core/http/app-env.js'
import { ApiError } from '../../core/http/errors.js'
import { fileResponse } from '../../core/http/file-response.js'
import { validate } from '../../core/http/validation.js'
import { catalogTracks } from '../library/catalog.js'
import { MODEL_IDS } from './generation/music-provider.js'
import type { TrackCursor } from './tracks.repository.js'
import type { TracksService } from './tracks.service.js'
import type { Track } from './tracks.table.js'

const CreateTrackBody = z.strictObject({
  prompt: z.string().trim().min(10).max(2000),
  // Omitted: the model picks the length ("Auto" in the composer).
  lengthMs: z.number().int().min(30_000).max(300_000).optional(),
  instrumental: z.boolean().default(false),
  modelId: z.enum(MODEL_IDS).optional(),
  quality: z.enum(['auto', 'deep', 'fast']).default('auto'),
  variants: z.union([z.literal(1), z.literal(2)]).default(1),
  customLyrics: z.string().trim().min(1).max(10000).optional(),
  remixTrackId: z
    .string()
    .min(1)
    .max(100)
    .refine(
      (id) => z.uuid().safeParse(id).success || catalogTracks.some((track) => track.id === id),
      { message: 'Choose an existing track' },
    )
    .optional(),
  referenceUploadId: z.uuid().optional(),
})

const IdempotencyHeader = z.object({
  'idempotency-key': z
    .string({ error: 'Send an Idempotency-Key header, the same on every retry' })
    .min(8)
    .max(100)
    .refine((key) => !key.includes(':variant:'), {
      message: 'Idempotency-Key cannot contain the reserved :variant: namespace',
    }),
})

const ListQuery = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(20),
  cursor: z.string().optional(),
})

const TrackParams = z.object({ id: z.uuid() })

/**
 * The signed-in user's tracks. Creating one queues a generation; the app then polls the track
 * until it is `done` or `failed`, and downloads the audio once.
 */
export const tracksRoutes = (tracks: TracksService, publicBaseUrl?: string) => {
  const audioUrl = (c: Context, id: string) =>
    `${publicBaseUrl ?? new URL(c.req.url).origin}/v1/tracks/${id}/audio`

  return new Hono<AppEnv>()
    .post(
      '/tracks',
      requireOnboarding,
      validate('header', IdempotencyHeader),
      validate('json', CreateTrackBody),
      async (c) => {
        const { track, created } = await tracks.create(
          c.get('user'),
          c.req.valid('header')['idempotency-key'],
          c.req.valid('json'),
        )
        c.header('Location', `/v1/tracks/${track.id}`)
        const siblings = await tracks.siblings(c.get('user'), track.generationId)
        return c.json(
          {
            ...toTrack(track, audioUrl(c, track.id)),
            tracks: siblings.map((item) => toTrack(item, audioUrl(c, item.id))),
          },
          created ? 202 : 200,
        )
      },
    )
    .get('/tracks', validate('query', ListQuery), async (c) => {
      const { limit, cursor } = c.req.valid('query')
      const page = await tracks.list(
        c.get('user'),
        limit,
        cursor ? decodeCursor(cursor) : undefined,
      )
      return c.json({
        tracks: page.tracks.map((track) => toTrack(track, audioUrl(c, track.id))),
        nextCursor: page.next ? encodeCursor(page.next) : null,
      })
    })
    .get('/tracks/:id', validate('param', TrackParams), async (c) => {
      const track = await tracks.get(c.get('user'), c.req.valid('param').id)
      return c.json({ ...toTrack(track, audioUrl(c, track.id)), words: track.words ?? [] })
    })
    .patch(
      '/tracks/:id',
      validate('param', TrackParams),
      validate('json', z.strictObject({ title: z.string().trim().min(1).max(80) })),
      async (c) => {
        const track = await tracks.rename(
          c.get('user'),
          c.req.valid('param').id,
          c.req.valid('json').title,
        )
        return c.json(toTrack(track, audioUrl(c, track.id)))
      },
    )
    .get('/tracks/:id/audio', validate('param', TrackParams), async (c) => {
      const file = await tracks.audio(c.get('user'), c.req.valid('param').id)
      return fileResponse(file, file.contentType ?? 'audio/mpeg', {
        method: c.req.method,
        range: c.req.header('Range'),
      })
    })
    .delete('/tracks/:id', validate('param', TrackParams), async (c) => {
      await tracks.delete(c.get('user'), c.req.valid('param').id)
      return c.body(null, 204)
    })
    .get('/me/quota', async (c) => {
      const quota = await tracks.quota(c.get('user'))
      return c.json({ ...quota, resetsAt: quota.resetsAt.toISOString() })
    })
}

/** The JSON the app sees. Word timings come only with a single track: lists stay small. */
const toTrack = (track: Track, audioUrl: string) => ({
  id: track.id,
  generationId: track.generationId,
  variantIndex: track.variantIndex,
  variants: track.variants,
  quality: track.quality,
  provider: track.providerName,
  customLyrics: track.customLyrics,
  referenceUploadId: track.referenceUploadId,
  remixTrackId: track.remixTrackId,
  status: track.status,
  prompt: track.prompt,
  lengthMs: track.lengthMs,
  instrumental: track.instrumental,
  modelId: track.modelId,
  title: track.title,
  durationMs: track.durationMs,
  genres: track.genres ?? [],
  explicit: track.explicit,
  audioUrl: track.status === 'done' ? audioUrl : null,
  error:
    track.status === 'failed'
      ? {
          code: track.errorCode,
          message: track.errorMessage,
          promptSuggestion: track.promptSuggestion,
        }
      : null,
  createdAt: track.createdAt.toISOString(),
  finishedAt: track.finishedAt?.toISOString() ?? null,
})

const CursorJson = z.object({ createdAt: z.iso.datetime(), id: z.uuid() })

function encodeCursor(cursor: TrackCursor): string {
  const json = JSON.stringify({ createdAt: cursor.createdAt.toISOString(), id: cursor.id })
  return Buffer.from(json).toString('base64url')
}

function decodeCursor(cursor: string): TrackCursor {
  try {
    const parsed = CursorJson.parse(JSON.parse(Buffer.from(cursor, 'base64url').toString()))
    return { createdAt: new Date(parsed.createdAt), id: parsed.id }
  } catch {
    throw new ApiError(400, 'VALIDATION', 'The cursor is not valid')
  }
}
