import { readFileSync } from 'node:fs'
import { z } from 'zod'
import { defaultHttpLimits, type HttpLimits } from './http/admission.js'

/** Where the data lives: a real Postgres server, or the embedded PGlite for local work and tests. */
export type DatabaseConfig =
  | { kind: 'postgres'; url: string }
  | { kind: 'pglite'; dataDir?: string }

/** The fields of a Firebase service account key that the Admin SDK needs. */
export type FirebaseServiceAccount = { projectId: string; clientEmail: string; privateKey: string }

/** `firebase` checks real ID tokens; `dev` accepts `dev:<name>` and is for local work only. */
export type AuthConfig =
  | { mode: 'firebase'; serviceAccount: FirebaseServiceAccount }
  | { mode: 'dev' }

export type GenerationConfig = {
  provider: 'mock' | 'elevenlabs'
  elevenlabs?: { apiKey: string; references: boolean }
  mockDelayMs: number
  concurrency: number
  timeoutMs: number
  dailyLimit: number
  allowedEmails?: string[]
  dailyPaidRequestsTotal?: number
}

export type Config = {
  env: 'development' | 'test' | 'production'
  server: { host: string; port: number; publicBaseUrl?: string }
  log: { level: LogLevel; pretty: boolean }
  database: DatabaseConfig
  auth: AuthConfig
  firebaseDeleteUsers: boolean
  generation: GenerationConfig
  storage: { audioDir: string }
  http: HttpLimits
}

export type LogLevel = z.infer<typeof LogLevel>
const LogLevel = z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent'])

/** `KEY=` in a .env file means "not set", not "set to an empty string". */
const optional = <T extends z.ZodType>(schema: T) =>
  z.preprocess((value) => (value === '' ? undefined : value), schema.optional())

const flag = z.enum(['true', 'false']).transform((value) => value === 'true')

/** The JSON key file from Firebase console → Project settings → Service accounts, as one line. */
const ServiceAccountJson = z
  .string()
  .transform((text, context) => {
    try {
      return JSON.parse(text) as unknown
    } catch {
      context.issues.push({ code: 'custom', message: 'must be the JSON key file', input: text })
      return z.NEVER
    }
  })
  .pipe(
    z.object({
      project_id: z.string().min(1),
      client_email: z.email(),
      private_key: z.string().startsWith('-----BEGIN PRIVATE KEY-----'),
    }),
  )
  .transform(
    (key): FirebaseServiceAccount => ({
      projectId: key.project_id,
      clientEmail: key.client_email,
      privateKey: key.private_key,
    }),
  )

const Env = z.object({
  NODE_ENV: optional(z.enum(['development', 'test', 'production'])),
  HOST: optional(z.string()),
  PORT: optional(z.coerce.number().int().min(1).max(65_535)),
  LOG_LEVEL: optional(LogLevel),
  LOG_PRETTY: optional(flag),
  DATABASE_URL: optional(z.url({ protocol: /^postgres(ql)?$/ })),
  DATA_DIR: optional(z.string()),
  AUDIO_DIR: optional(z.string()),
  AUTH_MODE: optional(z.enum(['firebase', 'dev'])),
  ALLOW_LAN_DEV_AUTH: optional(flag),
  PUBLIC_BASE_URL: optional(
    z.url({ protocol: /^https?$/ }).transform((url) => url.replace(/\/$/, '')),
  ),
  GENERATION_CONCURRENCY: optional(z.coerce.number().int().min(1).max(10)),
  GENERATION_TIMEOUT_MS: optional(z.coerce.number().int().min(10_000)),
  DAILY_GENERATIONS_PER_USER: optional(z.coerce.number().int().min(0)),
  DAILY_PAID_REQUESTS_TOTAL: optional(z.coerce.number().int().min(0)),
  GENERATION_ALLOWED_EMAILS: optional(
    z
      .string()
      .transform((value) => value.split(',').map((email) => email.trim().toLowerCase()))
      .pipe(z.array(z.email()).min(1))
      .transform((emails) => [...new Set(emails)]),
  ),
  FIREBASE_DELETE_USERS: optional(flag),
  MOCK_DELAY_MS: optional(z.coerce.number().int().min(0)),
  FIREBASE_SERVICE_ACCOUNT: optional(ServiceAccountJson),
  FIREBASE_SERVICE_ACCOUNT_FILE: optional(z.string().min(1)),
  HTTP_REQUESTS_PER_MINUTE: optional(z.coerce.number().int().min(1)),
  HTTP_OWNER_REQUESTS_PER_MINUTE: optional(z.coerce.number().int().min(1)),
  HTTP_CONCURRENT_UPLOADS: optional(z.coerce.number().int().min(1).max(100)),
  HTTP_OWNER_CONCURRENT_UPLOADS: optional(z.coerce.number().int().min(1).max(100)),
  MUSIC_PROVIDER: optional(z.enum(['mock', 'elevenlabs'])),
  ELEVENLABS_API_KEY: optional(z.string().trim().min(1)),
  ELEVENLABS_ENABLE_REFERENCES: optional(flag),
})

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1'])

function isPrivateIpv4(host: string): boolean {
  const parts = host.split('.')
  if (parts.length !== 4 || parts.some((part) => !/^(0|[1-9]\d{0,2})$/.test(part))) return false
  const octets = parts.map(Number)
  if (octets.some((octet) => octet > 255)) return false
  const first = octets[0]
  const second = octets[1]
  if (second === undefined) return false
  return (
    first === 10 ||
    (first === 172 && second >= 16 && second <= 31) ||
    (first === 192 && second === 168)
  )
}

export class ConfigError extends Error {
  constructor(readonly problems: string[]) {
    super(`Invalid configuration:\n${problems.map((problem) => `  - ${problem}`).join('\n')}`)
    this.name = 'ConfigError'
  }
}

/** Reads and checks the environment once at start, so a bad value fails loudly instead of later. */
export function loadConfig(environment: Record<string, string | undefined>): Config {
  let values = environment
  if (environment.FIREBASE_SERVICE_ACCOUNT_FILE && !environment.FIREBASE_SERVICE_ACCOUNT) {
    try {
      values = {
        ...environment,
        FIREBASE_SERVICE_ACCOUNT: readFileSync(environment.FIREBASE_SERVICE_ACCOUNT_FILE, 'utf8'),
      }
    } catch {
      throw new ConfigError(['FIREBASE_SERVICE_ACCOUNT_FILE: could not read credential file'])
    }
  }
  const parsed = Env.safeParse(values)
  if (!parsed.success) {
    throw new ConfigError(
      parsed.error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`),
    )
  }
  const env = parsed.data
  const nodeEnv = env.NODE_ENV ?? 'development'
  const host = env.HOST ?? '127.0.0.1'
  const authMode = env.AUTH_MODE ?? (nodeEnv === 'production' ? 'firebase' : 'dev')

  const problems: string[] = []
  if (nodeEnv === 'production' && !env.MUSIC_PROVIDER)
    problems.push('MUSIC_PROVIDER: explicitly choose elevenlabs or mock in production')
  if (nodeEnv === 'production' && !env.DATABASE_URL) {
    problems.push('DATABASE_URL: required in production; PGlite is for local work only')
  }
  if (nodeEnv === 'production' && !env.PUBLIC_BASE_URL) {
    problems.push(
      'PUBLIC_BASE_URL: required in production; behind a proxy the request origin is an internal address',
    )
  }
  if (nodeEnv === 'production' && !env.AUDIO_DIR) {
    problems.push(
      'AUDIO_DIR: required in production; point it at a persistent volume, or every deploy loses the audio',
    )
  }
  if (nodeEnv === 'production' && env.LOG_PRETTY) {
    problems.push(
      'LOG_PRETTY: pretty logs need pino-pretty, a dev dependency; production logs JSON',
    )
  }
  const allowLanDevAuth =
    env.ALLOW_LAN_DEV_AUTH === true && nodeEnv === 'development' && isPrivateIpv4(host)
  if (env.ALLOW_LAN_DEV_AUTH && (nodeEnv !== 'development' || !isPrivateIpv4(host))) {
    problems.push(
      'ALLOW_LAN_DEV_AUTH: requires NODE_ENV=development and a literal RFC1918 IPv4 HOST',
    )
  }
  if (
    authMode === 'dev' &&
    (nodeEnv === 'production' || (!LOOPBACK_HOSTS.has(host) && !allowLanDevAuth))
  ) {
    problems.push(
      'AUTH_MODE: dev sign-in lets anyone claim any account; use loopback or explicitly opt in to a private LAN development HOST, never production',
    )
  }
  if (authMode === 'firebase' && !env.FIREBASE_SERVICE_ACCOUNT) {
    problems.push('FIREBASE_SERVICE_ACCOUNT: required when AUTH_MODE is firebase')
  }
  if (
    nodeEnv === 'production' &&
    env.PUBLIC_BASE_URL &&
    !env.PUBLIC_BASE_URL.startsWith('https://')
  )
    problems.push('PUBLIC_BASE_URL: HTTPS is required in production')
  if (env.MUSIC_PROVIDER === 'elevenlabs' && !env.ELEVENLABS_API_KEY)
    problems.push('ELEVENLABS_API_KEY: required when MUSIC_PROVIDER is elevenlabs')
  // Real accounts reaching the paid provider need both guards, whatever NODE_ENV says: a hosted
  // environment can override the image's NODE_ENV. Local dev sign-in stays unrestricted.
  if (env.MUSIC_PROVIDER === 'elevenlabs' && authMode === 'firebase') {
    if (!env.GENERATION_ALLOWED_EMAILS)
      problems.push('GENERATION_ALLOWED_EMAILS: required with elevenlabs and Firebase sign-in')
    if (env.DAILY_PAID_REQUESTS_TOTAL === undefined)
      problems.push('DAILY_PAID_REQUESTS_TOTAL: required with elevenlabs and Firebase sign-in')
  }
  if (problems.length > 0) throw new ConfigError(problems)

  return {
    env: nodeEnv,
    server: { host, port: env.PORT ?? 8787, publicBaseUrl: env.PUBLIC_BASE_URL },
    log: { level: env.LOG_LEVEL ?? 'info', pretty: env.LOG_PRETTY ?? false },
    database: env.DATABASE_URL
      ? { kind: 'postgres', url: env.DATABASE_URL }
      : { kind: 'pglite', dataDir: `${env.DATA_DIR ?? './data'}/pglite` },
    auth:
      authMode === 'firebase' && env.FIREBASE_SERVICE_ACCOUNT
        ? { mode: 'firebase', serviceAccount: env.FIREBASE_SERVICE_ACCOUNT }
        : { mode: 'dev' },
    firebaseDeleteUsers: env.FIREBASE_DELETE_USERS ?? true,
    generation: {
      provider: env.MUSIC_PROVIDER ?? 'mock',
      ...(env.MUSIC_PROVIDER === 'elevenlabs' && env.ELEVENLABS_API_KEY
        ? {
            elevenlabs: {
              apiKey: env.ELEVENLABS_API_KEY,
              // Audio Reference comes with every paid plan, as the Music API itself does; false turns it off.
              references: env.ELEVENLABS_ENABLE_REFERENCES ?? true,
            },
          }
        : {}),
      mockDelayMs: env.MOCK_DELAY_MS ?? 8_000,
      concurrency: env.GENERATION_CONCURRENCY ?? 2,
      timeoutMs: env.GENERATION_TIMEOUT_MS ?? 5 * 60_000,
      dailyLimit: env.DAILY_GENERATIONS_PER_USER ?? 25,
      ...(env.GENERATION_ALLOWED_EMAILS !== undefined
        ? { allowedEmails: env.GENERATION_ALLOWED_EMAILS }
        : {}),
      ...(env.DAILY_PAID_REQUESTS_TOTAL !== undefined
        ? { dailyPaidRequestsTotal: env.DAILY_PAID_REQUESTS_TOTAL }
        : {}),
    },
    http: {
      requestsPerMinute: env.HTTP_REQUESTS_PER_MINUTE ?? defaultHttpLimits.requestsPerMinute,
      ownerRequestsPerMinute:
        env.HTTP_OWNER_REQUESTS_PER_MINUTE ?? defaultHttpLimits.ownerRequestsPerMinute,
      concurrentUploads: env.HTTP_CONCURRENT_UPLOADS ?? defaultHttpLimits.concurrentUploads,
      ownerConcurrentUploads:
        env.HTTP_OWNER_CONCURRENT_UPLOADS ?? defaultHttpLimits.ownerConcurrentUploads,
    },
    storage: { audioDir: env.AUDIO_DIR ?? `${env.DATA_DIR ?? './data'}/audio` },
  }
}
