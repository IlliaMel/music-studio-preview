import { eq, sql } from 'drizzle-orm'
import type { Database } from '../../core/db/database.js'
import { type User, users } from './users.table.js'

/** What the sign-in token tells us about a person. */
export type SignInIdentity = Pick<
  User,
  'firebaseUid' | 'email' | 'emailVerified' | 'signInProvider'
>

/** Onboarding answers to save; anything left undefined stays as it is. */
export type OnboardingAnswers = {
  displayName?: string
  marketingOptIn?: boolean
  confirmAge?: boolean
  acceptTermsVersion?: string
}

/** SQL for the `users` table and nothing else: the rules live in the service. */
export class UsersRepository {
  constructor(private readonly db: Database) {}

  async findById(id: string): Promise<User | undefined> {
    const [user] = await this.db.select().from(users).where(eq(users.id, id))
    return user
  }

  async findByFirebaseUid(firebaseUid: string): Promise<User | undefined> {
    const [user] = await this.db.select().from(users).where(eq(users.firebaseUid, firebaseUid))
    return user
  }

  /**
   * Creates the user on the first sign-in. When two requests of a new user arrive together,
   * both get the same row: the unique `firebase_uid` lets only one insert win.
   */
  async insertOrGet(identity: SignInIdentity, now: Date): Promise<User> {
    const [inserted] = await this.db
      .insert(users)
      .values({ ...identity, createdAt: now, updatedAt: now, lastSeenAt: now })
      .onConflictDoNothing({ target: users.firebaseUid })
      .returning()
    const user = inserted ?? (await this.findByFirebaseUid(identity.firebaseUid))
    if (!user) throw new Error(`User ${identity.firebaseUid} vanished right after insert`)
    return user
  }

  async updateSignIn(id: string, identity: SignInIdentity, now: Date): Promise<User | undefined> {
    return this.update(id, { ...identity, lastSeenAt: now, updatedAt: now })
  }

  /**
   * Saves onboarding answers in one statement. The time of the first age confirmation and of
   * accepting a terms version is decided by the database, so neither a repeated nor a
   * concurrent request can overwrite it.
   */
  async saveOnboarding(
    id: string,
    answers: OnboardingAnswers,
    now: Date,
  ): Promise<User | undefined> {
    const [user] = await this.db
      .update(users)
      .set({
        displayName: answers.displayName,
        marketingOptIn: answers.marketingOptIn,
        ...(answers.confirmAge && {
          ageConfirmedAt: sql`coalesce(${users.ageConfirmedAt}, ${now})`,
        }),
        ...(answers.acceptTermsVersion !== undefined && {
          termsVersion: answers.acceptTermsVersion,
          termsAcceptedAt: sql`case when ${users.termsVersion} is distinct from ${answers.acceptTermsVersion} then ${now} else ${users.termsAcceptedAt} end`,
        }),
        updatedAt: now,
      })
      .where(eq(users.id, id))
      .returning()
    return user
  }

  /** Returns false when there was no such user, so deleting twice is not an error. */
  async delete(id: string): Promise<boolean> {
    const deleted = await this.db.delete(users).where(eq(users.id, id)).returning({ id: users.id })
    return deleted.length > 0
  }

  private async update(
    id: string,
    values: Partial<typeof users.$inferInsert>,
  ): Promise<User | undefined> {
    const [user] = await this.db.update(users).set(values).where(eq(users.id, id)).returning()
    return user
  }
}
