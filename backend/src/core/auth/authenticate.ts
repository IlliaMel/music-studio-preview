import type { MiddlewareHandler } from 'hono'
import type { AppEnv, CurrentUser } from '../http/app-env.js'
import { ApiError } from '../http/errors.js'
import { type AuthProvider, InvalidTokenError, type VerifiedIdentity } from './auth-provider.js'

/** Turns a verified identity into our user, creating them on their first request. */
export type UserResolver = (identity: VerifiedIdentity) => Promise<CurrentUser>

/**
 * Requires `Authorization: Bearer <Firebase ID token>` and puts the signed-in user on the
 * request. There are no server sessions: every request proves who it is.
 */
export const authenticate =
  (auth: AuthProvider, resolveUser: UserResolver): MiddlewareHandler<AppEnv> =>
  async (c, next) => {
    const token = bearerToken(c.req.header('Authorization'))
    const identity = await auth.verifyIdToken(token).catch((error: unknown) => {
      if (error instanceof InvalidTokenError) {
        throw new ApiError(401, 'UNAUTHORIZED', 'The sign-in token is invalid or expired')
      }
      throw error
    })
    const user = await resolveUser(identity)
    if (user.deleting && !(c.req.method === 'DELETE' && c.req.path === '/v1/me'))
      throw new ApiError(
        409,
        'ACCOUNT_DELETING',
        'Retry account deletion to finish removing this account',
      )
    c.set('user', user)
    c.set('log', c.get('log').child({ userId: user.id }))
    await next()
  }

/** For actions requiring acceptance of the current terms. */
export const requireOnboarding: MiddlewareHandler<AppEnv> = async (c, next) => {
  if (!c.get('user').onboarded) {
    throw new ApiError(403, 'ONBOARDING_REQUIRED', 'Finish onboarding first')
  }
  await next()
}

/** Refuse generation inputs before parsing or storing them; other account features remain available. */
export const requireGenerationAccess: MiddlewareHandler<AppEnv> = async (c, next) => {
  if (!c.get('user').canGenerate) {
    throw new ApiError(403, 'GENERATION_NOT_ALLOWED', 'Music generation is currently by invitation')
  }
  await next()
}

function bearerToken(header: string | undefined): string {
  const [scheme, token, ...rest] = header?.trim().split(/\s+/) ?? []
  if (scheme?.toLowerCase() !== 'bearer' || !token || rest.length > 0) {
    throw new ApiError(
      401,
      'UNAUTHORIZED',
      'Send the sign-in token as Authorization: Bearer <token>',
    )
  }
  return token
}
