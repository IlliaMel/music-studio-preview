import type { MiddlewareHandler } from 'hono'
import type { AppEnv } from './app-env.js'
import { ApiError } from './errors.js'

export type HttpLimits = {
  requestsPerMinute: number
  ownerRequestsPerMinute: number
  concurrentUploads: number
  ownerConcurrentUploads: number
}
export const defaultHttpLimits: HttpLimits = {
  requestsPerMinute: 1200,
  ownerRequestsPerMinute: 240,
  concurrentUploads: 4,
  ownerConcurrentUploads: 2,
}
export const isBinaryUpload = (method: string, path: string) =>
  (method === 'POST' && path === '/v1/uploads') ||
  (method === 'PUT' && /^\/v1\/recordings\/[^/]+$/.test(path))

export function admission(limits: HttpLimits, now: () => number = Date.now) {
  let windowEnds = 0
  let requests = 0
  let uploads = 0
  const owners = new Map<string, { requests: number; ends: number; uploads: number }>()
  const reject = (retrySeconds: number): never => {
    throw new ApiError(429, 'RATE_LIMITED', 'Too many requests; retry later', {
      retryAfterSeconds: Math.max(1, retrySeconds),
    })
  }
  const global: MiddlewareHandler<AppEnv> = async (c, next) => {
    const time = now()
    if (time >= windowEnds) {
      requests = 0
      windowEnds = time + 60000
    }
    if (requests >= limits.requestsPerMinute) {
      c.header('Retry-After', String(Math.max(1, Math.ceil((windowEnds - time) / 1000))))
      reject(Math.ceil((windowEnds - time) / 1000))
    }
    requests++
    await next()
  }
  const owner: MiddlewareHandler<AppEnv> = async (c, next) => {
    const time = now()
    for (const [id, state] of owners) {
      if (state.ends <= time && state.uploads === 0) owners.delete(id)
    }
    const id = c.get('user').id
    let state = owners.get(id)
    if (!state) {
      if (owners.size >= 10000) {
        c.header('Retry-After', '60')
        reject(60)
      }
      state = { requests: 0, ends: time + 60000, uploads: 0 }
      owners.set(id, state)
    }
    if (time >= state.ends) {
      state.requests = 0
      state.ends = time + 60000
    }
    if (state.requests >= limits.ownerRequestsPerMinute) {
      c.header('Retry-After', String(Math.max(1, Math.ceil((state.ends - time) / 1000))))
      reject(Math.ceil((state.ends - time) / 1000))
    }
    state.requests++
    if (!isBinaryUpload(c.req.method, c.req.path)) return next()
    if (uploads >= limits.concurrentUploads || state.uploads >= limits.ownerConcurrentUploads) {
      c.header('Retry-After', '1')
      reject(1)
    }
    uploads++
    state.uploads++
    try {
      await next()
    } finally {
      uploads--
      state.uploads--
    }
  }
  return { global, owner }
}
