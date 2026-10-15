import type { AuthProvider, VerifiedIdentity } from '../../core/auth/auth-provider.js'
import type { CurrentUser } from '../../core/http/app-env.js'
import { ApiError } from '../../core/http/errors.js'
import type { SignInIdentity, UsersRepository } from './users.repository.js'
import type { User } from './users.table.js'

/** Bump when the terms change: everyone is asked to accept the new version again. */
export const TERMS_VERSION = '2026-10-14'

/** `last_seen_at` is refreshed at most this often, so reading a profile does not write each time. */
const LAST_SEEN_INTERVAL_MS = 60 * 60 * 1000

export type OnboardingStep = 'terms'

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
    /** Other features remove what they keep outside the database, such as audio files. */
    private readonly onAccountDeleted: (userId: string) => Promise<void> = async () => {},
    private readonly now: () => Date = () => new Date(),
  ) {}

  /** Finds the user behind a verified token and creates them on their very first request. */
  async resolve(identity: VerifiedIdentity): Promise<CurrentUser> {
    const signIn: SignInIdentity = {
      firebaseUid: identity.uid,
      displayName:
        identity.displayName?.trim().slice(0, 40) ||
        identity.email?.split('@')[0]?.slice(0, 40) ||
        'Music lover',
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
      deleting: user.deletionRequestedAt !== null,
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
    const updated = await this.repository.saveOnboarding(
      userId,
      {
        displayName: update.displayName,
        marketingOptIn: update.marketingOptIn,
        confirmAge: update.ageConfirmed,
        acceptTermsVersion: update.acceptedTermsVersion,
      },
      this.now(),
    )
    if (!updated) throw userNotFound()
    return updated
  }

  /**
   * Keep a durable deletion marker until storage cleanup and Firebase deletion succeed.
   * Other requests are blocked while DELETE /me can retry a failed cleanup.
   */
  async deleteAccount(user: CurrentUser): Promise<void> {
    if (!(await this.repository.requestDeletion(user.id, this.now()))) throw userNotFound()
    await this.finishDeletion(user)
  }

  pendingDeletions(): Promise<Pick<User, 'id'>[]> {
    return this.repository.pendingDeletions()
  }

  async recoverDeletion(userId: string): Promise<boolean> {
    const user = await this.repository.findById(userId)
    if (!user?.deletionRequestedAt) return false
    await this.finishDeletion({ id: user.id, firebaseUid: user.firebaseUid })
    return true
  }

  private async finishDeletion(user: Pick<CurrentUser, 'id' | 'firebaseUid'>): Promise<void> {
    await this.onAccountDeleted(user.id)
    await this.auth.deleteAccount(user.firebaseUid)
    await this.repository.delete(user.id)
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
  if (user.termsVersion !== TERMS_VERSION) missing.push('terms')
  return missing
}

const userNotFound = () => new ApiError(404, 'USER_NOT_FOUND', 'The account no longer exists')
