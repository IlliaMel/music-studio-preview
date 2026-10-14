import { z } from 'zod'

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

export type Config = {
  env: 'development' | 'test' | 'production'
  server: { host: string; port: number }
  log: { level: LogLevel; pretty: boolean }
  database: DatabaseConfig
  auth: AuthConfig
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
  AUTH_MODE: optional(z.enum(['firebase', 'dev'])),
  FIREBASE_SERVICE_ACCOUNT: optional(ServiceAccountJson),
})

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1'])

export class ConfigError extends Error {
  constructor(readonly problems: string[]) {
    super(`Invalid configuration:\n${problems.map((problem) => `  - ${problem}`).join('\n')}`)
    this.name = 'ConfigError'
  }
}

/** Reads and checks the environment once at start, so a bad value fails loudly instead of later. */
export function loadConfig(environment: Record<string, string | undefined>): Config {
  const parsed = Env.safeParse(environment)
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
  if (nodeEnv === 'production' && !env.DATABASE_URL) {
    problems.push('DATABASE_URL: required in production; PGlite is for local work only')
  }
  if (nodeEnv === 'production' && env.LOG_PRETTY) {
    problems.push(
      'LOG_PRETTY: pretty logs need pino-pretty, a dev dependency; production logs JSON',
    )
  }
  if (authMode === 'dev' && (nodeEnv === 'production' || !LOOPBACK_HOSTS.has(host))) {
    problems.push(
      'AUTH_MODE: dev sign-in lets anyone claim any account, so it runs only on a loopback HOST and never in production',
    )
  }
  if (authMode === 'firebase' && !env.FIREBASE_SERVICE_ACCOUNT) {
    problems.push('FIREBASE_SERVICE_ACCOUNT: required when AUTH_MODE is firebase')
  }
  if (problems.length > 0) throw new ConfigError(problems)

  return {
    env: nodeEnv,
    server: { host, port: env.PORT ?? 8787 },
    log: { level: env.LOG_LEVEL ?? 'info', pretty: env.LOG_PRETTY ?? false },
    database: env.DATABASE_URL
      ? { kind: 'postgres', url: env.DATABASE_URL }
      : { kind: 'pglite', dataDir: `${env.DATA_DIR ?? './data'}/pglite` },
    auth:
      authMode === 'firebase' && env.FIREBASE_SERVICE_ACCOUNT
        ? { mode: 'firebase', serviceAccount: env.FIREBASE_SERVICE_ACCOUNT }
        : { mode: 'dev' },
  }
}
