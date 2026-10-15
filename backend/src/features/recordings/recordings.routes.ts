import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv } from '../../core/http/app-env.js'
import { ApiError } from '../../core/http/errors.js'
import { fileResponse } from '../../core/http/file-response.js'
import { validate } from '../../core/http/validation.js'
import type { RecordingsService } from './recordings.service.js'
import type { Recording } from './recordings.table.js'

const Params = z.object({ id: z.uuid() })
const Title = z.string().trim().min(1).max(80)
const Duration = z.coerce.number().int().min(1).max(600000)
const Rename = z.strictObject({ title: Title })
export const recordingsRoutes = (service: RecordingsService, publicBaseUrl?: string) => {
  const dto = (row: Recording, requestUrl: string) => ({
    id: row.id,
    title: row.title,
    contentType: row.contentType,
    sizeBytes: row.sizeBytes,
    durationMs: row.durationMs,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString(),
    audioUrl: new URL(`/v1/recordings/${row.id}/audio`, publicBaseUrl ?? requestUrl).href,
  })
  return new Hono<AppEnv>()
    .get('/recordings', async (c) =>
      c.json({
        recordings: (await service.list(c.get('user').id)).map((row) => dto(row, c.req.url)),
      }),
    )
    .put('/recordings/:id', validate('param', Params), async (c) => {
      let decodedTitle: string
      try {
        decodedTitle = decodeURIComponent(c.req.header('X-Recording-Title') ?? '')
      } catch {
        throw new ApiError(400, 'VALIDATION', 'Invalid encoded recording title')
      }
      const title = Title.safeParse(decodedTitle)
      const duration = Duration.safeParse(c.req.header('X-Recording-Duration-Ms'))
      if (!title.success || !duration.success)
        throw new ApiError(
          400,
          'VALIDATION',
          'Send a title of 1 to 80 characters and duration of 1 to 600000 milliseconds',
        )
      const result = await service.save(
        c.get('user').id,
        c.req.valid('param').id,
        title.data,
        duration.data,
        c.req.header('Content-Type')?.split(';')[0]?.trim() ?? '',
        Buffer.from(await c.req.arrayBuffer()),
      )
      return c.json(dto(result.row, c.req.url), result.created ? 201 : 200)
    })
    .patch('/recordings/:id', validate('param', Params), validate('json', Rename), async (c) =>
      c.json(
        dto(
          await service.rename(
            c.get('user').id,
            c.req.valid('param').id,
            c.req.valid('json').title,
          ),
          c.req.url,
        ),
      ),
    )
    .get('/recordings/:id/audio', validate('param', Params), async (c) => {
      const { file, contentType } = await service.audio(c.get('user').id, c.req.valid('param').id)
      return fileResponse(file, contentType, { method: c.req.method, range: c.req.header('Range') })
    })
    .delete('/recordings/:id', validate('param', Params), async (c) => {
      await service.remove(c.get('user').id, c.req.valid('param').id)
      return c.body(null, 204)
    })
}
