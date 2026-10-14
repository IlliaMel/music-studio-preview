import { composeApp } from '../../src/compose.js'
import type { AuthProvider } from '../../src/core/auth/auth-provider.js'
import { DevAuthProvider } from '../../src/core/auth/dev-auth-provider.js'
import type { Database } from '../../src/core/db/database.js'
import type { Feature } from '../../src/core/http/app-env.js'
import { silentLogger } from './logger.js'

/** Dev sign-in that remembers which accounts the server asked Firebase to delete. */
export class RecordingAuthProvider extends DevAuthProvider {
  readonly deletedAccounts: string[] = []

  override async deleteAccount(uid: string): Promise<void> {
    this.deletedAccounts.push(uid)
  }
}

/** The app wired by the same `composeApp` as `main.ts`, with dev sign-in and test features. */
export function createTestApp(
  db: Database,
  {
    auth = new RecordingAuthProvider(),
    features = [],
  }: { auth?: AuthProvider; features?: Feature[] } = {},
) {
  const { app } = composeApp({ db, log: silentLogger, auth }, features)
  return { app, auth }
}

/** Request options for a user signed in with the dev token `dev:<name>`. */
export function signedInAs(
  name: string,
  { method = 'GET', body }: { method?: string; body?: unknown } = {},
): RequestInit {
  return {
    method,
    headers: {
      Authorization: `Bearer dev:${name}`,
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
    },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  }
}
