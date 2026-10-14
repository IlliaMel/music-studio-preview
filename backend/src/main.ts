import { serve } from '@hono/node-server'
import { composeApp } from './compose.js'
import type { AuthProvider } from './core/auth/auth-provider.js'
import { DevAuthProvider } from './core/auth/dev-auth-provider.js'
import { FirebaseAuthProvider } from './core/auth/firebase-auth-provider.js'
import { type Config, ConfigError, loadConfig } from './core/config.js'
import { openDatabase } from './core/db/database.js'
import { createLogger } from './core/logger.js'

// Reads the configuration, creates the real services and starts the server.
// The features and their wiring live in compose.ts, which the tests use as well.

loadEnvFileIfPresent()
const config = loadConfigOrExit()
const log = createLogger(config.log)

const database = await openDatabase(config.database)
await database.migrate()
log.info({ database: config.database.kind }, 'database is ready')

const auth: AuthProvider =
  config.auth.mode === 'firebase'
    ? FirebaseAuthProvider.fromServiceAccount(config.auth.serviceAccount)
    : new DevAuthProvider()
if (config.auth.mode === 'dev') {
  log.warn('dev sign-in is on: the token dev:<name> signs in as <name>')
}

const { app, features } = composeApp({ db: database.db, log, auth })
const server = serve(
  { fetch: app.fetch, hostname: config.server.host, port: config.server.port },
  (info) =>
    log.info(
      { url: `http://${info.address}:${info.port}`, features: features.map(({ name }) => name) },
      'listening',
    ),
)

let stopping = false
const shutdown = (signal: NodeJS.Signals) => {
  if (stopping) return
  stopping = true
  log.info({ signal }, 'shutting down')
  // Requests in flight get ten seconds to finish; after that the process exits anyway.
  const forceExit = setTimeout(() => process.exit(1), 10_000).unref()
  server.close(async () => {
    await database.close()
    clearTimeout(forceExit)
    process.exit(0)
  })
}
process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)

/** Values already set in the environment win over the file, as hosting platforms expect. */
function loadEnvFileIfPresent(): void {
  try {
    process.loadEnvFile()
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
  }
}

function loadConfigOrExit(): Config {
  try {
    return loadConfig(process.env)
  } catch (error) {
    if (!(error instanceof ConfigError)) throw error
    console.error(error.message)
    process.exit(1)
  }
}
