import { and, asc, eq, isNull } from 'drizzle-orm'
import type { Database } from '../../core/db/database.js'
import { tracks } from '../tracks/tracks.table.js'
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
    const [upload] = await this.db
      .insert(uploads)
      .values({ userId, contentType, sizeBytes: data.length, data: data.toString('base64') })
      .returning({ id: uploads.id, contentType: uploads.contentType, sizeBytes: uploads.sizeBytes })
    if (!upload) throw new Error('Insert returned no upload')
    return upload
  }
}
