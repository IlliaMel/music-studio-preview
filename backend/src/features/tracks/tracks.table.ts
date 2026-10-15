import { sql } from 'drizzle-orm'
import {
  boolean,
  index,
  integer,
  jsonb,
  pgEnum,
  pgTable,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from 'drizzle-orm/pg-core'
import type { Word } from './tracks.model.js'

export type { Word } from './tracks.model.js'

import { users } from '../users/users.table.js'

const timestampTz = (name: string) => timestamp(name, { withTimezone: true })

/** queued → running → done | failed. The table is the job queue too: the worker claims rows. */
export const trackStatus = pgEnum('track_status', ['queued', 'running', 'done', 'failed'])
export type TrackStatus = (typeof trackStatus.enumValues)[number]

export const tracks = pgTable(
  'tracks',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    // Chosen by the app per attempt and reused on network retries, so a retried request
    // returns the same track instead of starting, and paying for, a second generation.
    idempotencyKey: text('idempotency_key').notNull(),
    generationId: uuid('generation_id').notNull().defaultRandom(),
    variantIndex: integer('variant_index').notNull().default(0),
    variants: integer('variants').notNull().default(1),
    customLyrics: text('custom_lyrics'),
    referenceUploadId: uuid('reference_upload_id'),
    remixTrackId: text('reference_track_id'),
    status: trackStatus('status').notNull().default('queued'),

    // What was asked for.
    prompt: text('prompt').notNull(),
    // Null lets the model choose the length ("Auto" in the composer).
    lengthMs: integer('length_ms'),
    instrumental: boolean('instrumental').notNull(),
    modelId: text('model_id').notNull(),
    quality: text('quality').notNull().default('auto'),

    // What came back.
    title: text('title'),
    durationMs: integer('duration_ms'),
    genres: jsonb('genres').$type<string[]>(),
    explicit: boolean('explicit'),
    words: jsonb('words').$type<Word[]>(),
    // The provider's id for the song: remixing and editing a section start from it later.
    songId: text('song_id'),

    errorCode: text('error_code'),
    errorMessage: text('error_message'),
    promptSuggestion: text('prompt_suggestion'),

    createdAt: timestampTz('created_at').notNull().defaultNow(),
    updatedAt: timestampTz('updated_at').notNull().defaultNow(),
    startedAt: timestampTz('started_at'),
    finishedAt: timestampTz('finished_at'),
    // Deleting a track wipes its content but keeps the row, because a generation that was paid
    // for still counts against the day's limit. Deleting the account removes the rows for good.
    deletedAt: timestampTz('deleted_at'),
  },
  (table) => [
    uniqueIndex('tracks_user_idempotency_key').on(table.userId, table.idempotencyKey),
    index('tracks_user_created_at').on(table.userId, table.createdAt, table.id),
    // The worker looks for the oldest queued track; a partial index keeps that lookup tiny.
    index('tracks_queued_created_at').on(table.createdAt).where(sql`${table.status} = 'queued'`),
  ],
)

export type Track = typeof tracks.$inferSelect
