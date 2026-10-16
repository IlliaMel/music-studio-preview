import { sql } from 'drizzle-orm'
import { Hono, type MiddlewareHandler } from 'hono'
import { bodyLimit } from 'hono/body-limit'
import { requestId } from 'hono/request-id'
import type { Database } from './core/db/database.js'
import {
  admission,
  defaultHttpLimits,
  type HttpLimits,
  isBinaryUpload,
} from './core/http/admission.js'
import type { AppEnv, Feature } from './core/http/app-env.js'
import { ApiError, errorHandler, notFoundHandler } from './core/http/errors.js'
import { requestLogger } from './core/http/request-logger.js'
import type { Logger } from './core/logger.js'

export type AppDependencies = {
  db: Database
  log: Logger
  /** Runs before every `/v1` route and sets the signed-in user. */
  authenticate: MiddlewareHandler<AppEnv>
  features: readonly Feature[]
  http?: HttpLimits
}

const MAX_BODY_BYTES = 64 * 1024

/** Builds the HTTP app from ready dependencies, so tests can pass an in-memory database. */
export function createApp({
  db,
  log,
  authenticate,
  features,
  http = defaultHttpLimits,
}: AppDependencies): Hono<AppEnv> {
  const app = new Hono<AppEnv>()
  app.use(requestId())
  app.use(requestLogger(log))

  // The hosting platform polls this and restarts the server when the database is unreachable.
  app.get('/health', async (c) => {
    try {
      await db.execute(sql`select 1`)
      return c.json({ ok: true })
    } catch (error) {
      c.get('log').error({ err: error }, 'database is unreachable')
      return c.json({ ok: false }, 503)
    }
  })

  const api = new Hono<AppEnv>()
  const limits = admission(http)
  api.use(limits.global)
  api.use(authenticate)
  api.use(limits.owner)
  api.use((c, next) => {
    const binaryUpload = isBinaryUpload(c.req.method, c.req.path)
    const maxSize = binaryUpload ? 20 * 1024 * 1024 : MAX_BODY_BYTES
    return bodyLimit({
      maxSize,
      onError: () => {
        throw new ApiError(413, 'PAYLOAD_TOO_LARGE', `The body is larger than ${maxSize} bytes`)
      },
    })(c, next)
  })
  for (const feature of features) {
    api.route('/', feature.routes)
  }
  app.route('/v1', api)

  app.notFound(notFoundHandler)
  app.onError(errorHandler)
  return app
}
