import { zValidator } from '@hono/zod-validator'
import type { ValidationTargets } from 'hono'
import type { z } from 'zod'
import { ApiError } from './errors.js'

/**
 * Validates part of the request with a zod schema; the handler then reads the typed value
 * with `c.req.valid(target)`. A bad request becomes `400 VALIDATION` with one entry per problem.
 */
export const validate = <Target extends keyof ValidationTargets, Schema extends z.ZodType>(
  target: Target,
  schema: Schema,
) =>
  zValidator(target, schema, (result) => {
    if (!result.success) {
      throw new ApiError(
        400,
        'VALIDATION',
        'The request is not valid',
        result.error.issues.map((issue) => ({
          path: issue.path.join('.'),
          message: issue.message,
        })),
      )
    }
  })
