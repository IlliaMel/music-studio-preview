import type { ErrorHandler, NotFoundHandler } from 'hono'
import { HTTPException } from 'hono/http-exception'
import type { ContentfulStatusCode } from 'hono/utils/http-status'
import type { AppEnv } from './app-env.js'

/**
 * An error the client is meant to see. `code` is stable and the app maps it to its own text;
 * `message` is for logs and developers.
 */
export class ApiError extends Error {
  constructor(
    readonly status: ContentfulStatusCode,
    readonly code: string,
    message: string,
    readonly details?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export type ErrorBody = {
  error: { code: string; message: string; details?: unknown }
}

const errorBody = (code: string, message: string, details?: unknown): ErrorBody => ({
  error: { code, message, ...(details === undefined ? {} : { details }) },
})

export const errorHandler: ErrorHandler<AppEnv> = (error, c) => {
  if (error instanceof ApiError) {
    return c.json(errorBody(error.code, error.message, error.details), error.status)
  }
  if (error instanceof HTTPException) {
    return c.json(errorBody(`HTTP_${error.status}`, error.message), error.status)
  }
  // Anything else is a bug: log it with the stack, show the client nothing internal.
  c.get('log').error({ err: error }, 'unhandled error')
  return c.json(errorBody('INTERNAL', 'Something went wrong on our side'), 500)
}

export const notFoundHandler: NotFoundHandler<AppEnv> = (c) =>
  c.json(errorBody('NOT_FOUND', `No route for ${c.req.method} ${c.req.path}`), 404)
