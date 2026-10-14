import type { AuthProvider, VerifiedIdentity } from '../../core/auth/auth-provider.js'
import type { CurrentUser } from '../../core/http/app-env.js'
import { ApiError } from '../../core/http/errors.js'
import type { SignInIdentity, UsersRepository } from './users.repository.js'
import type { User } from './users.table.js'

/** Bump when the terms change: everyone is asked to accept the new version again. */
export const TERMS_VERSION = '2026-10-14'

/** `last_seen_at` is refreshed at most this often, so reading a profile does not write each time. */
const LAST_SEEN_INTERVAL_MS = 60 * 60 * 1000

export type OnboardingStep = 'displayName' | 'age' | 'terms'

export type ProfileUpdate = {
  displayName?: string
  /** Only `true`: once confirmed, the age cannot be taken back. */
  ageConfirmed?: true
  /** The version the user has just read and accepted; must be the current one. */
  acceptedTermsVersion?: string
  marketingOptIn?: boolean
}

export class UsersService {
  constructor(
    private readonly repository: UsersRepository,
    private readonly auth: AuthProvider,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /** Finds the user behind a verified token and creates them on their very first request. */
  async resolve(identity: VerifiedIdentity): Promise<CurrentUser> {
    const signIn: SignInIdentity = {
      firebaseUid: identity.uid,
      email: identity.email,
      emailVerified: identity.emailVerified,
      signInProvider: identity.signInProvider,
    }
    const existing = await this.repository.findByFirebaseUid(identity.uid)
    const user = existing
      ? await this.refreshSignIn(existing, signIn)
      : await this.repository.insertOrGet(signIn, this.now())
    return {
      id: user.id,
      firebaseUid: user.firebaseUid,
      onboarded: missingOnboardingSteps(user).length === 0,
    }
  }

  async getProfile(userId: string): Promise<User> {
    const user = await this.repository.findById(userId)
    if (!user) throw userNotFound()
    return user
  }

  async updateProfile(userId: string, update: ProfileUpdate): Promise<User> {
    if (
      update.acceptedTermsVersion !== undefined &&
      update.acceptedTermsVersion !== TERMS_VERSION
    ) {
      throw new ApiError(409, 'TERMS_OUTDATED', `The current terms are version ${TERMS_VERSION}`, {
        termsVersion: TERMS_VERSION,
      })
    }
    const current = await this.getProfile(userId)
    const now = this.now()
    const updated = await this.repository.updateProfile(
      userId,
      {
        displayName: update.displayName,
        marketingOptIn: update.marketingOptIn,
        // The first confirmation is the one that counts, so it is never overwritten.
        ...(update.ageConfirmed && !current.ageConfirmedAt && { ageConfirmedAt: now }),
        ...(update.acceptedTermsVersion &&
          current.termsVersion !== TERMS_VERSION && {
            termsVersion: TERMS_VERSION,
            termsAcceptedAt: now,
          }),
      },
      now,
    )
    if (!updated) throw userNotFound()
    return updated
  }

  /**
   * Deletes our data first, then the Firebase account. If the second step fails, the client
   * retries and both steps run again safely: each of them ignores what is already gone.
   */
  async deleteAccount(user: CurrentUser): Promise<void> {
    await this.repository.delete(user.id)
    await this.auth.deleteAccount(user.firebaseUid)
  }

  private async refreshSignIn(user: User, signIn: SignInIdentity): Promise<User> {
    const now = this.now()
    const changed =
      user.email !== signIn.email ||
      user.emailVerified !== signIn.emailVerified ||
      user.signInProvider !== signIn.signInProvider
    const stale = now.getTime() - user.lastSeenAt.getTime() >= LAST_SEEN_INTERVAL_MS
    if (!changed && !stale) return user
    return (await this.repository.updateSignIn(user.id, signIn, now)) ?? user
  }
}

export function missingOnboardingSteps(user: User): OnboardingStep[] {
  const missing: OnboardingStep[] = []
  if (!user.displayName) missing.push('displayName')
  if (!user.ageConfirmedAt) missing.push('age')
  if (user.termsVersion !== TERMS_VERSION) missing.push('terms')
  return missing
}

const userNotFound = () => new ApiError(404, 'USER_NOT_FOUND', 'The account no longer exists')
