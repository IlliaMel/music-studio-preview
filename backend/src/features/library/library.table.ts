import { integer, pgTable, primaryKey, text, timestamp, uuid } from 'drizzle-orm/pg-core'
import { users } from '../users/users.table.js'

export const playlists = pgTable('playlists', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
})
export const playlistTracks = pgTable(
  'playlist_tracks',
  {
    playlistId: uuid('playlist_id')
      .notNull()
      .references(() => playlists.id, { onDelete: 'cascade' }),
    trackId: text('track_id').notNull(),
    addedAt: timestamp('added_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [primaryKey({ columns: [table.playlistId, table.trackId] })],
)
export const likes = pgTable(
  'likes',
  {
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    trackId: text('track_id').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [primaryKey({ columns: [table.userId, table.trackId] })],
)
export const uploads = pgTable('uploads', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  contentType: text('content_type').notNull(),
  sizeBytes: integer('size_bytes').notNull(),
  data: text('data').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
})
