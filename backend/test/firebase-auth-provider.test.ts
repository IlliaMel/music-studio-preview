import type { DecodedIdToken } from 'firebase-admin/auth'
import { describe, expect, it } from 'vitest'
import { InvalidTokenError } from '../src/core/auth/auth-provider.js'
import {
  type FirebaseAuthApi,
  FirebaseAuthProvider,
} from '../src/core/auth/firebase-auth-provider.js'

/** Stands in for the Admin SDK: returns a fixed token or fails with a Firebase error code. */
class FakeFirebaseAuth implements FirebaseAuthApi {
  readonly deleted: string[] = []
  readonly revocationChecks: (boolean | undefined)[] = []

  constructor(
    private readonly outcome: { token?: Partial<DecodedIdToken>; errorCode?: string } = {},
  ) {}

  async verifyIdToken(_token: string, checkRevoked?: boolean): Promise<DecodedIdToken> {
    this.revocationChecks.push(checkRevoked)
    if (this.outcome.errorCode) throw firebaseError(this.outcome.errorCode)
    return {
      uid: 'uid-alice',
      firebase: { sign_in_provider: 'google.com', identities: {} },
      ...this.outcome.token,
    } as DecodedIdToken
  }

  async deleteUser(uid: string): Promise<void> {
    if (this.outcome.errorCode) throw firebaseError(this.outcome.errorCode)
    this.deleted.push(uid)
  }
}

const firebaseError = (code: string) => Object.assign(new Error(code), { code })

describe('FirebaseAuthProvider.verifyIdToken', () => {
  it('reads who signed in from the token', async () => {
    const provider = new FirebaseAuthProvider(
      new FakeFirebaseAuth({ token: { email: 'alice@example.com', email_verified: true } }),
    )

    expect(await provider.verifyIdToken('token')).toEqual({
      uid: 'uid-alice',
      email: 'alice@example.com',
      emailVerified: true,
      signInProvider: 'google.com',
    })
  })

  it('asks Firebase whether the account was deleted, disabled or signed out everywhere', async () => {
    const firebase = new FakeFirebaseAuth()

    await new FirebaseAuthProvider(firebase).verifyIdToken('token')

    expect(firebase.revocationChecks).toEqual([true])
  })

  it('copes with an account that has no email, as with a hidden Apple address', async () => {
    const provider = new FirebaseAuthProvider(new FakeFirebaseAuth())

    expect(await provider.verifyIdToken('token')).toMatchObject({
      email: null,
      emailVerified: false,
    })
  })

  it.each([
    'auth/id-token-expired',
    'auth/id-token-revoked',
    'auth/argument-error',
    'auth/invalid-argument',
    'auth/user-disabled',
    'auth/user-not-found',
  ])('treats %s as a bad token', async (errorCode) => {
    const provider = new FirebaseAuthProvider(new FakeFirebaseAuth({ errorCode }))

    await expect(provider.verifyIdToken('token')).rejects.toBeInstanceOf(InvalidTokenError)
  })

  it.each(['app/network-error', 'auth/invalid-credential', 'auth/internal-error'])(
    'passes %s on as our own failure',
    async (errorCode) => {
      const provider = new FirebaseAuthProvider(new FakeFirebaseAuth({ errorCode }))

      await expect(provider.verifyIdToken('token')).rejects.toMatchObject({ code: errorCode })
    },
  )
})

describe('FirebaseAuthProvider.deleteAccount', () => {
  it('deletes the Firebase user', async () => {
    const firebase = new FakeFirebaseAuth()

    await new FirebaseAuthProvider(firebase).deleteAccount('uid-alice')

    expect(firebase.deleted).toEqual(['uid-alice'])
  })

  it('succeeds when the user is already gone', async () => {
    const provider = new FirebaseAuthProvider(
      new FakeFirebaseAuth({ errorCode: 'auth/user-not-found' }),
    )

    await expect(provider.deleteAccount('uid-alice')).resolves.toBeUndefined()
  })

  it('fails on any other error, so the client retries', async () => {
    const provider = new FirebaseAuthProvider(
      new FakeFirebaseAuth({ errorCode: 'app/network-error' }),
    )

    await expect(provider.deleteAccount('uid-alice')).rejects.toMatchObject({
      code: 'app/network-error',
    })
  })
})
