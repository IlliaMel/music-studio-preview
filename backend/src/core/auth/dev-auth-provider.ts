import { type AuthProvider, InvalidTokenError, type VerifiedIdentity } from './auth-provider.js'

const DEV_TOKEN = /^dev:([a-z0-9-]{1,32})$/

/**
 * Sign-in without Firebase for local work, tests, CI and reviewers without our Firebase project:
 * the token `dev:alice` is the user Alice. The config allows it only on a loopback address and
 * never in production, because anyone can claim any name.
 */
export class DevAuthProvider implements AuthProvider {
  async verifyIdToken(token: string): Promise<VerifiedIdentity> {
    const name = DEV_TOKEN.exec(token)?.[1]
    if (!name) throw new InvalidTokenError('expected a development token like dev:alice')
    return {
      uid: `dev-${name}`,
      email: `${name}@dev.local`,
      emailVerified: true,
      signInProvider: 'dev',
    }
  }

  async deleteAccount(_uid: string): Promise<void> {
    // Development accounts exist only in our database, so there is nothing else to delete.
  }
}
