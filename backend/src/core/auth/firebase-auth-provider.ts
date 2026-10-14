import { cert, initializeApp } from 'firebase-admin/app'
import { type Auth, getAuth } from 'firebase-admin/auth'
import type { FirebaseServiceAccount } from '../config.js'
import { type AuthProvider, InvalidTokenError, type VerifiedIdentity } from './auth-provider.js'

/** The two calls we need from the Admin SDK, so tests can stand in for Firebase. */
export type FirebaseAuthApi = Pick<Auth, 'verifyIdToken' | 'deleteUser'>

// Problems with the token itself. Anything else (network, bad credentials) is our failure, not
// the client's, and must surface as a server error rather than as "sign in again".
const TOKEN_ERROR_CODES = new Set([
  'auth/argument-error',
  'auth/invalid-argument',
  'auth/id-token-expired',
  'auth/id-token-revoked',
  'auth/user-disabled',
])

export class FirebaseAuthProvider implements AuthProvider {
  constructor(private readonly auth: FirebaseAuthApi) {}

  static fromServiceAccount(serviceAccount: FirebaseServiceAccount): FirebaseAuthProvider {
    const app = initializeApp({
      credential: cert(serviceAccount),
      projectId: serviceAccount.projectId,
    })
    return new FirebaseAuthProvider(getAuth(app))
  }

  async verifyIdToken(token: string): Promise<VerifiedIdentity> {
    try {
      // Checks the signature against Google's public keys, the expiry and that the token was
      // issued for our project. The keys are cached, so this rarely touches the network.
      const decoded = await this.auth.verifyIdToken(token)
      return {
        uid: decoded.uid,
        email: decoded.email ?? null,
        emailVerified: decoded.email_verified ?? false,
        signInProvider: decoded.firebase.sign_in_provider,
      }
    } catch (error) {
      const code = firebaseErrorCode(error)
      if (code && TOKEN_ERROR_CODES.has(code)) throw new InvalidTokenError(code)
      throw error
    }
  }

  async deleteAccount(uid: string): Promise<void> {
    try {
      await this.auth.deleteUser(uid)
    } catch (error) {
      if (firebaseErrorCode(error) !== 'auth/user-not-found') throw error
    }
  }
}

const firebaseErrorCode = (error: unknown): string | undefined =>
  typeof error === 'object' && error !== null && 'code' in error && typeof error.code === 'string'
    ? error.code
    : undefined
