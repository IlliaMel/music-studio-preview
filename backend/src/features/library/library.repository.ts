import { and, asc, eq, isNull, lt, notExists, sql } from 'drizzle-orm'
import type { Database } from '../../core/db/database.js'
import { ApiError } from '../../core/http/errors.js'
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
  listPlaylists(userId: string) {
    return this.db
      .select()
      .from(playlists)
      .where(eq(playlists.userId, userId))
      .orderBy(asc(playlists.createdAt))
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
  async claimRemoteUpload(id: string): Promise<boolean> {
    const updated = await this.db
      .update(uploads)
      .set({ providerAttempted: true })
      .where(and(eq(uploads.id, id), eq(uploads.providerAttempted, false)))
      .returning({ id: uploads.id })
    return updated.length === 1
  }
  async saveRemoteUpload(id: string, providerSongId: string, durationMs: number): Promise<void> {
    await this.db.update(uploads).set({ providerSongId, durationMs }).where(eq(uploads.id, id))
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
