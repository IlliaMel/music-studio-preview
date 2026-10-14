import type { Hono } from 'hono'
import type { Logger } from '../logger.js'

/** Values every request carries through the middleware chain, read with `c.get(...)`. */
export type AppEnv = {
  Variables: {
    requestId: string
    /** Logger that already includes the request id. */
    log: Logger
  }
}

/**
 * A feature plugs its routes into `/v1`. Adding one means creating it in `main.ts`
 * and passing it to `createApp`; nothing else in the app changes.
 */
export type Feature = {
  name: string
  routes: Hono<AppEnv>
}
