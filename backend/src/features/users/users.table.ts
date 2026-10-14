import { boolean, pgTable, text, timestamp, uuid } from 'drizzle-orm/pg-core'

const timestampTz = (name: string) => timestamp(name, { withTimezone: true })

export const users = pgTable('users', {
  // Our own id: other tables reference it, so the sign-in provider can change later.
  id: uuid('id').primaryKey().defaultRandom(),
  firebaseUid: text('firebase_uid').notNull().unique(),
  // Copied from the sign-in token. Apple can hide the address, so it may be missing.
  email: text('email'),
  emailVerified: boolean('email_verified').notNull().default(false),
  // As Firebase reports it: `google.com`, `password`, `apple.com`…
  signInProvider: text('sign_in_provider').notNull(),

  // Filled in during onboarding.
  displayName: text('display_name'),
  ageConfirmedAt: timestampTz('age_confirmed_at'),
  termsVersion: text('terms_version'),
  termsAcceptedAt: timestampTz('terms_accepted_at'),
  marketingOptIn: boolean('marketing_opt_in').notNull().default(false),

  createdAt: timestampTz('created_at').notNull().defaultNow(),
  updatedAt: timestampTz('updated_at').notNull().defaultNow(),
  lastSeenAt: timestampTz('last_seen_at').notNull().defaultNow(),
})

export type User = typeof users.$inferSelect
