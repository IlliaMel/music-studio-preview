import {
  and,
  asc,
  count,
  desc,
  eq,
  gte,
  inArray,
  isNull,
  lt,
  not,
  or,
  type SQL,
  sql,
} from 'drizzle-orm'
import type { Database } from '../../core/db/database.js'
import { type Track, tracks } from './tracks.table.js'

export type NewTrack = Pick<
  Track,
  'userId' | 'idempotencyKey' | 'prompt' | 'lengthMs' | 'instrumental' | 'modelId'
>

export type TrackResult = Pick<
  Track,
  'title' | 'durationMs' | 'genres' | 'explicit' | 'words' | 'songId'
>

export type TrackFailure = { code: string; message: string; promptSuggestion: string | null }

/** Position in a user's list, newest first: tracks created before this one. */
export type TrackCursor = { createdAt: Date; id: string }

export type CreateOutcome =
  | { kind: 'created'; track: Track }
  | { kind: 'existing'; track: Track }
  | { kind: 'limit-reached'; used: number }

/**
 * Failures the provider does not charge for: it refused the request before generating anything.
 * Every other outcome counts against the limit, including a timeout or an interruption, where
 * the song may well have been generated and paid for.
 */
const FREE_FAILURES = [
  'INVALID_PROMPT',
  'QUOTA_EXCEEDED',
  'PROVIDER_AUTH',
  'RATE_LIMITED',
  'PROVIDER_UNAVAILABLE',
]

/** The one definition of "counts against today's limit", for enforcing it and for showing it. */
const countsTowardLimit = (userId: string, since: Date): SQL | undefined =>
  and(
    eq(tracks.userId, userId),
    gte(tracks.createdAt, since),
    // Deleted tracks still count: deleting must not hand back a generation that was paid for.
    not(
      and(
        eq(tracks.status, 'failed'),
        inArray(sql`coalesce(${tracks.errorCode}, '')`, FREE_FAILURES),
      ) as SQL,
    ),
  )

const visible = isNull(tracks.deletedAt)

/** SQL for the `tracks` table, which is also the queue the generation worker takes jobs from. */
export class TracksRepository {
  constructor(private readonly db: Database) {}

  /**
   * Creates a track unless the same idempotency key was already used, or the user has spent
   * the day's generations. A per-user lock serialises the check and the insert, so two
   * requests at the same moment cannot both squeeze under the limit.
   */
  async create(
    input: NewTrack,
    limit: { since: Date; max: number },
    now: Date,
  ): Promise<CreateOutcome> {
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${input.userId}, 0))`)

      const [existing] = await tx
        .select()
        .from(tracks)
        .where(
          and(eq(tracks.userId, input.userId), eq(tracks.idempotencyKey, input.idempotencyKey)),
        )
      if (existing) return { kind: 'existing', track: existing }

      const [{ used = 0 } = {}] = await tx
        .select({ used: count() })
        .from(tracks)
        .where(countsTowardLimit(input.userId, limit.since))
      if (used >= limit.max) return { kind: 'limit-reached', used }

      const [track] = await tx
        .insert(tracks)
        .values({ ...input, createdAt: now, updatedAt: now })
        .returning()
      if (!track) throw new Error('Insert returned no row')
      return { kind: 'created', track }
    })
  }

  async findForUser(userId: string, id: string): Promise<Track | undefined> {
    const [track] = await this.db
      .select()
      .from(tracks)
      .where(and(eq(tracks.userId, userId), eq(tracks.id, id), visible))
    return track
  }

  async listForUser(userId: string, limit: number, before?: TrackCursor): Promise<Track[]> {
    return this.db
      .select()
      .from(tracks)
      .where(
        and(
          eq(tracks.userId, userId),
          visible,
          before &&
            or(
              lt(tracks.createdAt, before.createdAt),
              and(eq(tracks.createdAt, before.createdAt), lt(tracks.id, before.id)),
            ),
        ),
      )
      .orderBy(desc(tracks.createdAt), desc(tracks.id))
      .limit(limit)
  }

  async countTowardLimit(userId: string, since: Date): Promise<number> {
    const [{ used = 0 } = {}] = await this.db
      .select({ used: count() })
      .from(tracks)
      .where(countsTowardLimit(userId, since))
    return used
  }

  /**
   * Takes the oldest queued track and marks it running, in one statement. `skip locked` lets
   * several workers take different tracks without waiting on each other.
   */
  async claimNext(now: Date): Promise<Track | undefined> {
    const oldestQueued = this.db
      .select({ id: tracks.id })
      .from(tracks)
      .where(eq(tracks.status, 'queued'))
      .orderBy(asc(tracks.createdAt))
      .limit(1)
      .for('update', { skipLocked: true })
    const [track] = await this.db
      .update(tracks)
      .set({ status: 'running', startedAt: now, updatedAt: now })
      .where(inArray(tracks.id, oldestQueued))
      .returning()
    return track
  }

  /** False when the track was deleted meanwhile: its audio is no longer wanted. */
  async complete(id: string, result: TrackResult, now: Date): Promise<boolean> {
    return this.finish(id, { ...result, status: 'done' }, now)
  }

  async fail(id: string, failure: TrackFailure, now: Date): Promise<boolean> {
    return this.finish(
      id,
      {
        status: 'failed',
        errorCode: failure.code,
        errorMessage: failure.message,
        promptSuggestion: failure.promptSuggestion,
      },
      now,
    )
  }

  /** Puts a track the server stopped working on back in the queue, for the next worker. */
  async requeue(id: string, now: Date): Promise<boolean> {
    const requeued = await this.db
      .update(tracks)
      .set({ status: 'queued', startedAt: null, updatedAt: now })
      .where(and(eq(tracks.id, id), eq(tracks.status, 'running')))
      .returning({ id: tracks.id })
    return requeued.length > 0
  }

  /** Fails tracks whose worker vanished without saying so (a crash) mid-generation. */
  async failAbandoned(startedBefore: Date, failure: TrackFailure, now: Date): Promise<number> {
    const abandoned = await this.db
      .update(tracks)
      .set({
        status: 'failed',
        errorCode: failure.code,
        errorMessage: failure.message,
        promptSuggestion: failure.promptSuggestion,
        finishedAt: now,
        updatedAt: now,
      })
      .where(and(eq(tracks.status, 'running'), lt(tracks.startedAt, startedBefore)))
      .returning({ id: tracks.id })
    return abandoned.length
  }

  /**
   * Wipes what the user wrote and got back, and hides the track. The row stays for the daily
   * limit; it disappears with the account.
   */
  async softDeleteForUser(userId: string, id: string, now: Date): Promise<boolean> {
    const deleted = await this.db
      .update(tracks)
      .set({
        prompt: '',
        title: null,
        genres: null,
        words: null,
        errorMessage: null,
        promptSuggestion: null,
        deletedAt: now,
        updatedAt: now,
      })
      .where(and(eq(tracks.userId, userId), eq(tracks.id, id), visible))
      .returning({ id: tracks.id })
    return deleted.length > 0
  }

  /** Every track of a user, deleted ones included: account deletion removes their files first. */
  async listIdsForUser(userId: string): Promise<string[]> {
    const rows = await this.db
      .select({ id: tracks.id })
      .from(tracks)
      .where(eq(tracks.userId, userId))
    return rows.map(({ id }) => id)
  }

  async deleteAllForUser(userId: string): Promise<void> {
    await this.db.delete(tracks).where(eq(tracks.userId, userId))
  }

  /** Only a running, not deleted track can finish; anything else was decided meanwhile. */
  private async finish(
    id: string,
    values: Partial<typeof tracks.$inferInsert>,
    now: Date,
  ): Promise<boolean> {
    const finished = await this.db
      .update(tracks)
      .set({ ...values, finishedAt: now, updatedAt: now })
      .where(and(eq(tracks.id, id), eq(tracks.status, 'running'), visible))
      .returning({ id: tracks.id })
    return finished.length > 0
  }
}
