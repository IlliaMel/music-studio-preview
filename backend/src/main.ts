import { serve } from '@hono/node-server'
import { createApp } from './app.js'
import type { AuthProvider } from './core/auth/auth-provider.js'
import { authenticate } from './core/auth/authenticate.js'
import { DevAuthProvider } from './core/auth/dev-auth-provider.js'
import { FirebaseAuthProvider } from './core/auth/firebase-auth-provider.js'
import { type Config, ConfigError, loadConfig } from './core/config.js'
import { openDatabase } from './core/db/database.js'
import type { Feature } from './core/http/app-env.js'
import { createLogger } from './core/logger.js'
import { createUsersFeature } from './features/users/index.js'

// The composition root: the only place that creates objects and wires them together.

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

const users = createUsersFeature({ db: database.db, auth })
const features: Feature[] = [users.feature]

const app = createApp({
  db: database.db,
  log,
  authenticate: authenticate(auth, (identity) => users.service.resolve(identity)),
  features,
})
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
