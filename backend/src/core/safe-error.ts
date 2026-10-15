const TYPES = new Set([
  'Error',
  'TypeError',
  'RangeError',
  'SyntaxError',
  'AbortError',
  'TimeoutError',
  'ApiError',
  'ProviderError',
  'DrizzleError',
  'DrizzleQueryError',
  'TransactionRollbackError',
])
const CODES = new Set([
  'ENOENT',
  'EACCES',
  'EBUSY',
  'ENOSPC',
  'ECONNRESET',
  'ECONNREFUSED',
  'ETIMEDOUT',
  'EPIPE',
  'EIO',
  'ENOTDIR',
  'EISDIR',
  'EPERM',
  'ENOTEMPTY',
  'INVALID_PROMPT',
  'QUOTA_EXCEEDED',
  'RATE_LIMITED',
  'PROVIDER_UNAVAILABLE',
  'PROVIDER_AUTH',
  'TIMEOUT',
  'INTERRUPTED',
  'INTERNAL',
  'UNSUPPORTED_MODE',
  'INVALID_REFERENCE',
  'PROVIDER_OUTCOME_UNKNOWN',
])

type SafeError = { type: string; code?: string; cause?: SafeError }

export function serializeError(value: unknown): SafeError {
  return classify(value, new WeakSet(), 0)
}

function classify(value: unknown, seen: WeakSet<object>, depth: number): SafeError {
  if (!(value instanceof Error) || seen.has(value)) return { type: 'Error' }
  seen.add(value)
  const constructorName = value.constructor.name
  const type = TYPES.has(constructorName) ? constructorName : 'Error'
  const result: SafeError = { type }
  const code = (value as Error & { code?: unknown }).code
  if (typeof code === 'string' && (CODES.has(code) || /^[0-9A-Z]{5}$/.test(code)))
    result.code = code
  if (depth < 3 && value.cause instanceof Error)
    result.cause = classify(value.cause, seen, depth + 1)
  return result
}
