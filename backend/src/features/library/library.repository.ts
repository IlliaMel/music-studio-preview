import { and, asc, eq, gte, isNull, lt, notExists, sql } from 'drizzle-orm'
import type { Database } from '../../core/db/database.js'
import { ApiError } from '../../core/http/errors.js'
import { paidGenerationDailyUsage } from '../tracks/paid-usage.table.js'
import { tracks } from '../tracks/tracks.table.js'
import { users } from '../users/users.table.js'
import { likes, playlists, playlistTracks, uploads } from './library.table.js'

export class LibraryRepository {
  constructor(private readonly db: Database) {}
  async likedIds(userId: string) {
    return (
      await this.db
        .select({ id: likes.trackId })
        .from(likes)
        .where(eq(likes.userId, userId))
        .orderBy(asc(likes.createdAt))
    ).map((row) => row.id)
  }
  async setLiked(
    userId: string,
    trackId: string,
    liked: boolean,
    generated: boolean,
  ): Promise<boolean> {
    return this.db.transaction(async (tx) => {
      if (liked && generated) {
        const [track] = await tx
          .select({ id: tracks.id })
          .from(tracks)
          .where(and(eq(tracks.id, trackId), eq(tracks.userId, userId), isNull(tracks.deletedAt)))
          .for('share')
        if (!track) return false
      }
      if (liked) await tx.insert(likes).values({ userId, trackId }).onConflictDoNothing()
      else await tx.delete(likes).where(and(eq(likes.userId, userId), eq(likes.trackId, trackId)))
      return true
    })
  }
  async listPlaylists(userId: string) {
    const rows = await this.db
      .select({ playlist: playlists, trackId: playlistTracks.trackId })
      .from(playlists)
      .leftJoin(playlistTracks, eq(playlistTracks.playlistId, playlists.id))
      .where(eq(playlists.userId, userId))
      .orderBy(
        asc(playlists.createdAt),
        asc(playlists.id),
        asc(playlistTracks.addedAt),
        asc(playlistTracks.trackId),
      )
    const result = new Map<string, typeof playlists.$inferSelect & { trackIds: string[] }>()
    for (const row of rows) {
      let playlist = result.get(row.playlist.id)
      if (!playlist) {
        playlist = { ...row.playlist, trackIds: [] }
        result.set(playlist.id, playlist)
      }
      if (row.trackId !== null) playlist.trackIds.push(row.trackId)
    }
    return [...result.values()]
  }
  async findPlaylist(userId: string, id: string) {
    const [playlist] = await this.db
      .select()
      .from(playlists)
      .where(and(eq(playlists.id, id), eq(playlists.userId, userId)))
    return playlist
  }
  async playlistTrackIds(id: string) {
    return (
      await this.db
        .select()
        .from(playlistTracks)
        .where(eq(playlistTracks.playlistId, id))
        .orderBy(asc(playlistTracks.addedAt), asc(playlistTracks.trackId))
    ).map((row) => row.trackId)
  }
  async createPlaylist(userId: string, name: string) {
    const [playlist] = await this.db.insert(playlists).values({ userId, name }).returning()
    if (!playlist) throw new Error('Insert returned no playlist')
    return playlist
  }
  async renamePlaylist(userId: string, id: string, name: string) {
    const [playlist] = await this.db
      .update(playlists)
      .set({ name })
      .where(and(eq(playlists.id, id), eq(playlists.userId, userId)))
      .returning()
    return playlist
  }
  async deletePlaylist(userId: string, id: string) {
    await this.db.delete(playlists).where(and(eq(playlists.id, id), eq(playlists.userId, userId)))
  }
  async setPlaylistTrack(
    userId: string,
    id: string,
    trackId: string,
    added: boolean,
    generated: boolean,
  ): Promise<'ok' | 'playlist-not-found' | 'track-not-found'> {
    return this.db.transaction(async (tx) => {
      const [playlist] = await tx
        .select({ id: playlists.id })
        .from(playlists)
        .where(and(eq(playlists.id, id), eq(playlists.userId, userId)))
        .for('share')
      if (!playlist) return 'playlist-not-found'
      if (added && generated) {
        const [track] = await tx
          .select({ id: tracks.id })
          .from(tracks)
          .where(and(eq(tracks.id, trackId), eq(tracks.userId, userId), isNull(tracks.deletedAt)))
          .for('share')
        if (!track) return 'track-not-found'
      }
      if (added)
        await tx.insert(playlistTracks).values({ playlistId: id, trackId }).onConflictDoNothing()
      else
        await tx
          .delete(playlistTracks)
          .where(and(eq(playlistTracks.playlistId, id), eq(playlistTracks.trackId, trackId)))
      return 'ok'
    })
  }
  async saveUpload(userId: string, contentType: string, data: Buffer) {
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${userId}, 0))`)
      const [owner] = await tx
        .select({ deleting: users.deletionRequestedAt })
        .from(users)
        .where(eq(users.id, userId))
      if (!owner || owner.deleting)
        throw new ApiError(409, 'ACCOUNT_DELETING', 'Account cleanup is in progress')
      await tx.delete(uploads).where(
        and(
          eq(uploads.userId, userId),
          lt(uploads.createdAt, new Date(Date.now() - 86400000)),
          notExists(
            tx
              .select({ id: tracks.id })
              .from(tracks)
              .where(
                and(
                  eq(tracks.referenceUploadId, uploads.id),
                  isNull(tracks.deletedAt),
                  sql`${tracks.status} in ('queued','running')`,
                ),
              ),
          ),
        ),
      )
      const [budget] = await tx
        .select({
          count: sql<number>`count(*)::int`,
          bytes: sql<number>`coalesce(sum(${uploads.sizeBytes}), 0)::int`,
        })
        .from(uploads)
        .where(eq(uploads.userId, userId))
      if ((budget?.count ?? 0) >= 20 || (budget?.bytes ?? 0) + data.length > 100 * 1024 * 1024)
        throw new ApiError(
          429,
          'UPLOAD_LIMIT',
          'Delete an unused upload or wait for its 24 hour expiry; limit is 20 files and 100 MB per account',
        )
      const [upload] = await tx
        .insert(uploads)
        .values({ userId, contentType, sizeBytes: data.length, data: data.toString('base64') })
        .returning({
          id: uploads.id,
          contentType: uploads.contentType,
          sizeBytes: uploads.sizeBytes,
        })
      if (!upload) throw new Error('Insert returned no upload')
      return upload
    })
  }
  async findUpload(userId: string, id: string) {
    const [upload] = await this.db
      .select()
      .from(uploads)
      .where(and(eq(uploads.id, id), eq(uploads.userId, userId)))
    return upload
  }
  async claimRemoteUpload(
    id: string,
    maxPaidRequestsTotal?: number,
    attemptId = crypto.randomUUID(),
  ): Promise<boolean> {
    return this.db.transaction(async (tx) => {
      await tx.execute(
        sql`select pg_advisory_xact_lock(hashtextextended('studio:paid-generation-budget', 0))`,
      )
      const [source] = await tx
        .select({ userId: uploads.userId })
        .from(uploads)
        .where(eq(uploads.id, id))
      if (!source) return false
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${source.userId}, 0))`)
      const [upload] = await tx
        .select({
          attempted: uploads.providerAttempted,
          songId: uploads.providerSongId,
          paidReservationDay: uploads.paidReservationDay,
        })
        .from(uploads)
        .where(eq(uploads.id, id))
        .for('update')
      if (!upload || upload.attempted || upload.songId) return false
      let day = upload.paidReservationDay
      if (day === null) {
        // A queued sibling may outlive a confirmed upload refusal and its refund. It
        // needs a fresh reservation before trying again, under the same global lock.
        day = new Date().toISOString().slice(0, 10)
        const [usage] = await tx
          .select()
          .from(paidGenerationDailyUsage)
          .where(eq(paidGenerationDailyUsage.day, day))
        if (
          maxPaidRequestsTotal !== undefined &&
          (usage?.reservedVariants ?? 0) >= maxPaidRequestsTotal
        ) {
          throw new ApiError(
            429,
            'SERVICE_DAILY_LIMIT',
            'The paid upload reservation limit is exhausted',
          )
        }
        await tx
          .insert(paidGenerationDailyUsage)
          .values({ day, reservedVariants: 1 })
          .onConflictDoUpdate({
            target: paidGenerationDailyUsage.day,
            set: {
              reservedVariants: sql`${paidGenerationDailyUsage.reservedVariants} + 1`,
            },
          })
      }
      await tx
        .update(uploads)
        .set({ providerAttempted: true, providerAttemptId: attemptId, paidReservationDay: day })
        .where(eq(uploads.id, id))
      return true
    })
  }
  async saveRemoteUpload(
    id: string,
    providerSongId: string,
    durationMs: number,
    attemptId?: string,
  ): Promise<void> {
    if (!attemptId) throw new Error('A remote upload result needs its attempt identity')
    const saved = await this.db
      .update(uploads)
      .set({ providerSongId, durationMs })
      .where(
        and(
          eq(uploads.id, id),
          eq(uploads.providerAttemptId, attemptId),
          eq(uploads.providerAttempted, true),
        ),
      )
      .returning({ id: uploads.id })
    if (!saved.length) throw new Error('The upload attempt is no longer current')
  }

  /** Only a proven credit/auth rejection permits another upload attempt and a reservation refund. */
  async rejectRemoteUpload(id: string, attemptId?: string): Promise<void> {
    if (!attemptId) return
    await this.db.transaction(async (tx) => {
      await tx.execute(
        sql`select pg_advisory_xact_lock(hashtextextended('studio:paid-generation-budget', 0))`,
      )
      const [source] = await tx
        .select({ userId: uploads.userId })
        .from(uploads)
        .where(eq(uploads.id, id))
      if (!source) return
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${source.userId}, 0))`)
      const [upload] = await tx
        .select({
          providerAttempted: uploads.providerAttempted,
          providerSongId: uploads.providerSongId,
          paidReservationDay: uploads.paidReservationDay,
          providerAttemptId: uploads.providerAttemptId,
        })
        .from(uploads)
        .where(eq(uploads.id, id))
        .for('update')
      if (
        !upload?.providerAttempted ||
        upload.providerSongId ||
        upload.providerAttemptId !== attemptId
      )
        return
      await tx
        .update(uploads)
        .set({ providerAttempted: false, providerAttemptId: null, paidReservationDay: null })
        .where(eq(uploads.id, id))
      if (upload.paidReservationDay !== null) {
        await tx
          .update(paidGenerationDailyUsage)
          .set({ reservedVariants: sql`${paidGenerationDailyUsage.reservedVariants} - 1` })
          .where(
            and(
              eq(paidGenerationDailyUsage.day, upload.paidReservationDay),
              gte(paidGenerationDailyUsage.reservedVariants, 1),
            ),
          )
      }
    })
  }
  async deleteUpload(userId: string, id: string): Promise<void> {
    await this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${userId}, 0))`)
      const [upload] = await tx
        .select({ id: uploads.id })
        .from(uploads)
        .where(and(eq(uploads.id, id), eq(uploads.userId, userId)))
      if (!upload) throw new ApiError(404, 'UPLOAD_NOT_FOUND', 'No such upload')
      const [active] = await tx
        .select({ id: tracks.id })
        .from(tracks)
        .where(
          and(
            eq(tracks.referenceUploadId, id),
            isNull(tracks.deletedAt),
            sql`${tracks.status} in ('queued','running')`,
          ),
        )
        .limit(1)
      if (active)
        throw new ApiError(
          409,
          'UPLOAD_IN_USE',
          'Wait for generation to finish before deleting the reference',
        )
      await tx.delete(uploads).where(eq(uploads.id, id))
    })
  }
  async purgeExpiredUploads(): Promise<void> {
    const cutoff = new Date(Date.now() - 86400000)
    const owners = await this.db
      .selectDistinct({ userId: uploads.userId })
      .from(uploads)
      .where(lt(uploads.createdAt, cutoff))
    for (const owner of owners) {
      await this.db.transaction(async (tx) => {
        await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${owner.userId}, 0))`)
        await tx.delete(uploads).where(
          and(
            eq(uploads.userId, owner.userId),
            lt(uploads.createdAt, cutoff),
            notExists(
              tx
                .select({ id: tracks.id })
                .from(tracks)
                .where(
                  and(
                    eq(tracks.referenceUploadId, uploads.id),
                    isNull(tracks.deletedAt),
                    sql`${tracks.status} in ('queued','running')`,
                  ),
                ),
            ),
          ),
        )
      })
    }
  }
}
