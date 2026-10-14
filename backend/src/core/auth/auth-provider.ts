/** Who a sign-in token belongs to, as the identity provider vouches for it. */
export type VerifiedIdentity = {
  uid: string
  email: string | null
  emailVerified: boolean
  /** As the provider reports it: `google.com`, `password`, `apple.com`… */
  signInProvider: string
}

/**
 * Checks sign-in tokens and removes accounts. The server never sees passwords: the app signs in
 * with Firebase and sends the resulting ID token with every request.
 */
export interface AuthProvider {
  verifyIdToken(token: string): Promise<VerifiedIdentity>
  /** Succeeds when the account is already gone, so a retried deletion can finish. */
  deleteAccount(uid: string): Promise<void>
}

/** The token is malformed, expired or revoked: the app has to refresh it or sign in again. */
export class InvalidTokenError extends Error {
  constructor(readonly reason: string) {
    super(`Invalid sign-in token: ${reason}`)
    this.name = 'InvalidTokenError'
  }
}
