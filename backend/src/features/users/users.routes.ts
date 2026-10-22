import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv } from '../../core/http/app-env.js'
import { validate } from '../../core/http/validation.js'
import { missingOnboardingSteps, TERMS_VERSION, type UsersService } from './users.service.js'
import type { User } from './users.table.js'

const ProfileUpdateBody = z
  .strictObject({
    displayName: z.string().trim().min(1).max(40).optional(),
    ageConfirmed: z.literal(true).optional(),
    acceptedTermsVersion: z.string().min(1).optional(),
    marketingOptIn: z.boolean().optional(),
  })
  .refine((body) => Object.values(body).some((value) => value !== undefined), {
    message: 'Send at least one field to change',
  })

/** The signed-in user's own profile: read it, fill it in during onboarding, delete the account. */
export const usersRoutes = (users: UsersService) =>
  new Hono<AppEnv>()
    .get('/me', async (c) =>
      c.json(toProfile(await users.getProfile(c.get('user').id), c.get('user').canGenerate)),
    )
    .patch('/me', validate('json', ProfileUpdateBody), async (c) =>
      c.json(
        toProfile(
          await users.updateProfile(c.get('user').id, c.req.valid('json')),
          c.get('user').canGenerate,
        ),
      ),
    )
    .delete('/me', async (c) => {
      await users.deleteAccount(c.get('user'))
      return c.body(null, 204)
    })

/** The JSON the app sees. Timestamps of consent stay on the server; the app needs only yes/no. */
const toProfile = (user: User, canGenerate: boolean) => {
  const missing = missingOnboardingSteps(user)
  return {
    id: user.id,
    email: user.email,
    emailVerified: user.emailVerified,
    canGenerate,
    signInProvider: user.signInProvider,
    displayName: user.displayName,
    ageConfirmed: user.ageConfirmedAt !== null,
    termsVersion: user.termsVersion,
    marketingOptIn: user.marketingOptIn,
    onboarding: { complete: missing.length === 0, missing, termsVersion: TERMS_VERSION },
    createdAt: user.createdAt.toISOString(),
  }
}
