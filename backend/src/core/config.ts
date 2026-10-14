import { z } from 'zod'

/** Where the data lives: a real Postgres server, or the embedded PGlite for local work and tests. */
export type DatabaseConfig =
  | { kind: 'postgres'; url: string }
  | { kind: 'pglite'; dataDir?: string }

export type Config = {
  env: 'development' | 'test' | 'production'
  server: { host: string; port: number }
  log: { level: LogLevel; pretty: boolean }
  database: DatabaseConfig
}

export type LogLevel = z.infer<typeof LogLevel>
const LogLevel = z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent'])

/** `KEY=` in a .env file means "not set", not "set to an empty string". */
const optional = <T extends z.ZodType>(schema: T) =>
  z.preprocess((value) => (value === '' ? undefined : value), schema.optional())

const flag = z.enum(['true', 'false']).transform((value) => value === 'true')

const Env = z.object({
  NODE_ENV: optional(z.enum(['development', 'test', 'production'])),
  HOST: optional(z.string()),
  PORT: optional(z.coerce.number().int().min(1).max(65_535)),
  LOG_LEVEL: optional(LogLevel),
  LOG_PRETTY: optional(flag),
  DATABASE_URL: optional(z.url({ protocol: /^postgres(ql)?$/ })),
  DATA_DIR: optional(z.string()),
})

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

  const problems: string[] = []
  if (nodeEnv === 'production' && !env.DATABASE_URL) {
    problems.push('DATABASE_URL: required in production; PGlite is for local work only')
  }
  if (nodeEnv === 'production' && env.LOG_PRETTY) {
    problems.push(
      'LOG_PRETTY: pretty logs need pino-pretty, a dev dependency; production logs JSON',
    )
  }
  if (problems.length > 0) throw new ConfigError(problems)

  return {
    env: nodeEnv,
    server: { host: env.HOST ?? '127.0.0.1', port: env.PORT ?? 8787 },
    log: { level: env.LOG_LEVEL ?? 'info', pretty: env.LOG_PRETTY ?? false },
    database: env.DATABASE_URL
      ? { kind: 'postgres', url: env.DATABASE_URL }
      : { kind: 'pglite', dataDir: `${env.DATA_DIR ?? './data'}/pglite` },
  }
}
