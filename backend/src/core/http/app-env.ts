import type { Hono } from 'hono'
import type { Logger } from '../logger.js'

/** The signed-in user as every route sees it; features load more through the users service. */
export type CurrentUser = {
  id: string
  firebaseUid: string
  /** The current terms have been accepted. */
  onboarded: boolean
  deleting?: boolean
}

/** Values every request carries through the middleware chain, read with `c.get(...)`. */
export type AppEnv = {
  Variables: {
    requestId: string
    /** Logger that already includes the request id, and the user id once signed in. */
    log: Logger
    /** Set by `authenticate` on every `/v1` route; absent only on public routes like `/health`. */
    user: CurrentUser
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
