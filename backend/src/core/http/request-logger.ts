import type { MiddlewareHandler } from 'hono'
import type { Logger } from '../logger.js'
import type { AppEnv } from './app-env.js'

/** Gives every request a logger tagged with its id and writes one line when the request ends. */
export const requestLogger =
  (log: Logger): MiddlewareHandler<AppEnv> =>
  async (c, next) => {
    const startedAt = performance.now()
    c.set('log', log.child({ requestId: c.get('requestId') }))

    await next()

    c.get('log').info(
      {
        method: c.req.method,
        path: c.req.path,
        status: c.res.status,
        durationMs: Math.round(performance.now() - startedAt),
      },
      'request',
    )
  }
