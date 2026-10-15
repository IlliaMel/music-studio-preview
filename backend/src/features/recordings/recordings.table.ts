import { boolean, integer, pgTable, primaryKey, text, timestamp, uuid } from 'drizzle-orm/pg-core'
import { users } from '../users/users.table.js'

export const recordings = pgTable(
  'recordings',
  {
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    id: uuid('id').notNull(),
    title: text('title').notNull(),
    originalTitle: text('original_title').notNull(),
    contentType: text('content_type').notNull(),
    sizeBytes: integer('size_bytes').notNull(),
    durationMs: integer('duration_ms').notNull(),
    sha256: text('sha256').notNull(),
    ready: boolean('ready').notNull().default(false),
    audioRemoved: boolean('audio_removed').notNull().default(false),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    deletedAt: timestamp('deleted_at', { withTimezone: true }),
  },
  (table) => [primaryKey({ columns: [table.userId, table.id] })],
)
export type Recording = typeof recordings.$inferSelect
