import { sql } from 'drizzle-orm'
import { Hono, type MiddlewareHandler } from 'hono'
import { bodyLimit } from 'hono/body-limit'
import { requestId } from 'hono/request-id'
import type { Database } from './core/db/database.js'
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
}

const MAX_BODY_BYTES = 64 * 1024

/** Builds the HTTP app from ready dependencies, so tests can pass an in-memory database. */
export function createApp({ db, log, authenticate, features }: AppDependencies): Hono<AppEnv> {
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
  api.use(
    bodyLimit({
      maxSize: MAX_BODY_BYTES,
      onError: () => {
        throw new ApiError(
          413,
          'PAYLOAD_TOO_LARGE',
          `The body is larger than ${MAX_BODY_BYTES} bytes`,
        )
      },
    }),
  )
  api.use(authenticate)
  for (const feature of features) {
    api.route('/', feature.routes)
  }
  app.route('/v1', api)

  app.notFound(notFoundHandler)
  app.onError(errorHandler)
  return app
}
