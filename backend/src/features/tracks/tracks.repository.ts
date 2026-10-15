import {
  and,
  asc,
  countDistinct,
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
import { likes, playlistTracks, uploads } from '../library/library.table.js'
import { users } from '../users/users.table.js'
import { type Track, tracks } from './tracks.table.js'

export type NewTrack = Pick<
  Track,
  'userId' | 'idempotencyKey' | 'prompt' | 'lengthMs' | 'instrumental' | 'modelId'
> &
  Partial<
    Pick<
      Track,
      | 'customLyrics'
      | 'remixTrackId'
      | 'referenceUploadId'
      | 'variants'
      | 'quality'
      | 'providerName'
    >
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
  | { kind: 'account-deleting' }
  | { kind: 'upload-not-found' }

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
        not(eq(tracks.providerName, 'elevenlabs')),
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

      const [owner] = await tx
        .select({ deletionRequestedAt: users.deletionRequestedAt })
        .from(users)
        .where(eq(users.id, input.userId))
      if (!owner || owner.deletionRequestedAt) return { kind: 'account-deleting' }

      const [existing] = await tx
        .select()
        .from(tracks)
        .where(
          and(eq(tracks.userId, input.userId), eq(tracks.idempotencyKey, input.idempotencyKey)),
        )
      if (existing) return { kind: 'existing', track: existing }

      if (input.referenceUploadId) {
        const [upload] = await tx
          .select({ id: uploads.id })
          .from(uploads)
          .where(
            and(
              eq(uploads.id, input.referenceUploadId),
              eq(uploads.userId, input.userId),
              gte(uploads.createdAt, new Date(now.getTime() - 86400000)),
            ),
          )
        if (!upload) return { kind: 'upload-not-found' }
      }
      const [{ used = 0 } = {}] = await tx
        .select({ used: countDistinct(tracks.generationId) })
        .from(tracks)
        .where(countsTowardLimit(input.userId, limit.since))
      if (used >= limit.max) return { kind: 'limit-reached', used }

      const generationId = crypto.randomUUID()
      const variants = input.variants ?? 1
      const [track] = await tx
        .insert(tracks)
        .values(
          Array.from({ length: variants }, (_, variantIndex) => ({
            ...input,
            generationId,
            variants,
            variantIndex,
            idempotencyKey:
              variantIndex === 0
                ? input.idempotencyKey
                : `${input.idempotencyKey}:variant:${variantIndex}`,
            createdAt: now,
            updatedAt: now,
          })),
        )
        .returning()
      if (!track) throw new Error('Insert returned no row')
      return { kind: 'created', track }
    })
  }

  async renameForUser(
    userId: string,
    id: string,
    title: string,
    now: Date,
  ): Promise<Track | undefined> {
    const [track] = await this.db
      .update(tracks)
      .set({ title, updatedAt: now })
      .where(
        and(
          eq(tracks.userId, userId),
          eq(tracks.id, id),
          inArray(tracks.status, ['done', 'failed']),
          visible,
        ),
      )
      .returning()
    return track
  }

  async findByKey(userId: string, key: string): Promise<Track | undefined> {
    const [track] = await this.db
      .select()
      .from(tracks)
      .where(and(eq(tracks.userId, userId), eq(tracks.idempotencyKey, key)))
    return track
  }

  async ownsUpload(userId: string, id: string): Promise<boolean> {
    const [upload] = await this.db
      .select({ id: uploads.id })
      .from(uploads)
      .where(
        and(
          eq(uploads.id, id),
          eq(uploads.userId, userId),
          gte(uploads.createdAt, new Date(Date.now() - 24 * 60 * 60_000)),
        ),
      )
    return Boolean(upload)
  }

  async siblings(userId: string, generationId: string): Promise<Track[]> {
    return this.db
      .select()
      .from(tracks)
      .where(and(eq(tracks.userId, userId), eq(tracks.generationId, generationId), visible))
      .orderBy(asc(tracks.variantIndex))
  }

  async findForUser(
    userId: string,
    id: string,
    includeDeleted = false,
  ): Promise<Track | undefined> {
    const [track] = await this.db
      .select()
      .from(tracks)
      .where(
        and(eq(tracks.userId, userId), eq(tracks.id, id), includeDeleted ? undefined : visible),
      )
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
      .select({ used: countDistinct(tracks.generationId) })
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
      .where(and(eq(tracks.status, 'queued'), visible))
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
  async complete(
    id: string,
    result: TrackResult,
    now: Date,
    saveAudio?: () => Promise<void>,
  ): Promise<boolean> {
    if (!saveAudio) return this.finish(id, { ...result, status: 'done' }, now)
    return this.db.transaction(async (tx) => {
      const [active] = await tx
        .select({ id: tracks.id })
        .from(tracks)
        .where(and(eq(tracks.id, id), eq(tracks.status, 'running'), visible))
        .for('update')
      if (!active) return false
      await saveAudio()
      const updated = await tx
        .update(tracks)
        .set({ ...result, status: 'done', finishedAt: now, updatedAt: now })
        .where(eq(tracks.id, id))
        .returning({ id: tracks.id })
      return updated.length > 0
    })
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
      .where(and(eq(tracks.id, id), eq(tracks.status, 'running'), visible))
      .returning({ id: tracks.id })
    return requeued.length > 0
  }

  /** Fails tracks whose worker vanished without saying so (a crash) mid-generation. */
  async failAbandoned(startedBefore: Date, failure: TrackFailure, now: Date): Promise<number> {
    const abandoned = await this.db
      .update(tracks)
      .set({
        status: 'failed',
        errorCode: sql`case when ${tracks.providerName} = 'elevenlabs' then 'PROVIDER_OUTCOME_UNKNOWN' else ${failure.code} end`,
        errorMessage: sql`case when ${tracks.providerName} = 'elevenlabs' then 'Provider outcome unknown after restart; check ElevenLabs request history before creating another generation' else ${failure.message} end`,
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
    return this.db.transaction(async (tx) => {
      const deleted = await tx
        .update(tracks)
        .set({
          prompt: '',
          title: null,
          genres: null,
          words: null,
          customLyrics: null,
          referenceUploadId: null,
          remixTrackId: null,
          errorMessage: null,
          promptSuggestion: null,
          deletedAt: now,
          updatedAt: now,
        })
        .where(and(eq(tracks.userId, userId), eq(tracks.id, id), visible))
        .returning({ id: tracks.id })
      if (deleted.length === 0) return false
      await tx.delete(likes).where(and(eq(likes.userId, userId), eq(likes.trackId, id)))
      await tx.delete(playlistTracks).where(eq(playlistTracks.trackId, id))
      return true
    })
  }

  /** Every track of a user, deleted ones included: account deletion removes their files first. */
  async listIdsForUser(userId: string): Promise<string[]> {
    const rows = await this.db
      .select({ id: tracks.id })
      .from(tracks)
      .where(eq(tracks.userId, userId))
    return rows.map(({ id }) => id)
  }

  async markAllDeletedForUser(userId: string, now: Date): Promise<void> {
    await this.db
      .update(tracks)
      .set({ deletedAt: now, updatedAt: now })
      .where(and(eq(tracks.userId, userId), visible))
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
