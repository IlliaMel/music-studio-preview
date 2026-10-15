import { and, desc, eq, isNull, lt, or, sql } from 'drizzle-orm'
import type { Database } from '../../core/db/database.js'
import { ApiError } from '../../core/http/errors.js'
import { users } from '../users/users.table.js'
import { type Recording, recordings } from './recordings.table.js'

export type RecordingInput = Pick<
  Recording,
  'id' | 'originalTitle' | 'contentType' | 'sizeBytes' | 'durationMs' | 'sha256'
>
export class RecordingsRepository {
  constructor(private readonly db: Database) {}
  list(userId: string) {
    return this.db
      .select()
      .from(recordings)
      .where(
        and(
          eq(recordings.userId, userId),
          eq(recordings.ready, true),
          isNull(recordings.deletedAt),
        ),
      )
      .orderBy(desc(recordings.createdAt), desc(recordings.id))
  }
  async reclaimStalled(
    userId: string,
    removeAudio: (row: Recording) => Promise<void>,
  ): Promise<void> {
    await this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${userId}, 0))`)
      await this.requireOwner(tx, userId)
      const stale = await tx
        .select()
        .from(recordings)
        .where(
          and(
            eq(recordings.userId, userId),
            eq(recordings.ready, false),
            isNull(recordings.deletedAt),
            lt(recordings.updatedAt, new Date(Date.now() - 86400000)),
          ),
        )
      for (const row of stale) {
        await removeAudio(row)
        await tx
          .delete(recordings)
          .where(and(eq(recordings.userId, userId), eq(recordings.id, row.id)))
      }
    })
  }
  async find(userId: string, id: string) {
    const [row] = await this.db
      .select()
      .from(recordings)
      .where(and(eq(recordings.userId, userId), eq(recordings.id, id)))
    return row
  }
  async reserve(
    userId: string,
    input: RecordingInput,
  ): Promise<{ row: Recording; created: boolean }> {
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${userId}, 0))`)
      await this.requireOwner(tx, userId)
      const [existing] = await tx
        .select()
        .from(recordings)
        .where(and(eq(recordings.userId, userId), eq(recordings.id, input.id)))
      if (existing) {
        if (existing.deletedAt)
          throw new ApiError(
            409,
            'RECORDING_DELETED',
            'This recording was deleted; use a new recording ID',
          )
        if (
          existing.sha256 !== input.sha256 ||
          existing.originalTitle !== input.originalTitle ||
          existing.contentType !== input.contentType ||
          existing.durationMs !== input.durationMs ||
          existing.sizeBytes !== input.sizeBytes
        )
          throw new ApiError(
            409,
            'RECORDING_ID_REUSED',
            'The recording ID belongs to a different original upload',
          )
        return { row: existing, created: false }
      }
      const [budget] = await tx
        .select({
          count: sql<number>`count(*)::int`,
          bytes: sql<number>`coalesce(sum(${recordings.sizeBytes}), 0)::int`,
        })
        .from(recordings)
        .where(
          and(
            eq(recordings.userId, userId),
            or(isNull(recordings.deletedAt), eq(recordings.audioRemoved, false)),
          ),
        )
      if ((budget?.count ?? 0) >= 20 || (budget?.bytes ?? 0) + input.sizeBytes > 100 * 1024 * 1024)
        throw new ApiError(
          429,
          'RECORDING_LIMIT',
          'Delete an unused recording; limit is 20 recordings and 100 MB per account',
        )
      const [row] = await tx
        .insert(recordings)
        .values({ ...input, userId, title: input.originalTitle })
        .returning()
      if (!row) throw new Error('Recording insert returned no row')
      return { row, created: true }
    })
  }
  async complete(userId: string, id: string, save: () => Promise<void>): Promise<Recording> {
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${userId}, 0))`)
      await this.requireOwner(tx, userId)
      const [row] = await tx
        .select()
        .from(recordings)
        .where(and(eq(recordings.userId, userId), eq(recordings.id, id)))
      if (!row || row.deletedAt)
        throw new ApiError(409, 'RECORDING_DELETED', 'This recording was deleted')
      if (row.ready) return row
      await save()
      const [saved] = await tx
        .update(recordings)
        .set({ ready: true, updatedAt: new Date() })
        .where(and(eq(recordings.userId, userId), eq(recordings.id, id)))
        .returning()
      if (!saved) throw new Error('Recording vanished during save')
      return saved
    })
  }
  async rename(userId: string, id: string, title: string) {
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${userId}, 0))`)
      await this.requireOwner(tx, userId)
      const [row] = await tx
        .update(recordings)
        .set({ title, updatedAt: new Date() })
        .where(
          and(
            eq(recordings.userId, userId),
            eq(recordings.id, id),
            eq(recordings.ready, true),
            isNull(recordings.deletedAt),
          ),
        )
        .returning()
      if (!row) throw recordingNotFound()
      return row
    })
  }
  async remove(userId: string, id: string, removeAudio: () => Promise<void>): Promise<void> {
    await this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${userId}, 0))`)
      await this.requireOwner(tx, userId)
      const [row] = await tx
        .select()
        .from(recordings)
        .where(and(eq(recordings.userId, userId), eq(recordings.id, id)))
      if (!row) throw recordingNotFound()
      await tx
        .update(recordings)
        .set({ deletedAt: row.deletedAt ?? new Date(), updatedAt: new Date() })
        .where(and(eq(recordings.userId, userId), eq(recordings.id, id)))
    })
    await removeAudio()
    await this.db
      .update(recordings)
      .set({ audioRemoved: true })
      .where(and(eq(recordings.userId, userId), eq(recordings.id, id)))
  }
  async allForDeletion(userId: string) {
    await this.db
      .update(recordings)
      .set({ deletedAt: new Date(), updatedAt: new Date() })
      .where(eq(recordings.userId, userId))
    return this.db.select().from(recordings).where(eq(recordings.userId, userId))
  }
  private async requireOwner(
    tx: Parameters<Parameters<Database['transaction']>[0]>[0],
    userId: string,
  ) {
    const [owner] = await tx
      .select({ deleting: users.deletionRequestedAt })
      .from(users)
      .where(eq(users.id, userId))
    if (!owner || owner.deleting)
      throw new ApiError(409, 'ACCOUNT_DELETING', 'Account cleanup is in progress')
  }
}
export const recordingNotFound = () => new ApiError(404, 'RECORDING_NOT_FOUND', 'No such recording')
