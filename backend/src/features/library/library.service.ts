import { z } from 'zod'
import { ApiError } from '../../core/http/errors.js'
import { catalogTracks } from './catalog.js'
import type { LibraryRepository } from './library.repository.js'
import type { playlists } from './library.table.js'

const audioTypes = new Set([
  'audio/mpeg',
  'audio/mp4',
  'audio/wav',
  'audio/x-wav',
  'audio/aac',
  'audio/ogg',
  'audio/webm',
])
export class LibraryService {
  constructor(private readonly repository: LibraryRepository) {}
  likedIds(userId: string) {
    return this.repository.likedIds(userId)
  }
  async setLiked(userId: string, id: string, liked: boolean) {
    const generated = liked ? this.isGeneratedTrack(id) : false
    if (!(await this.repository.setLiked(userId, id, liked, generated))) throw trackNotFound()
  }
  async listPlaylists(userId: string) {
    return Promise.all(
      (await this.repository.listPlaylists(userId)).map((playlist) => this.toPlaylist(playlist)),
    )
  }
  async playlist(userId: string, id: string) {
    return this.toPlaylist(await this.requirePlaylist(userId, id))
  }
  async createPlaylist(userId: string, name: string) {
    return this.toPlaylist(await this.repository.createPlaylist(userId, name))
  }
  async renamePlaylist(userId: string, id: string, name: string) {
    const playlist = await this.repository.renamePlaylist(userId, id, name)
    if (!playlist) throw playlistNotFound()
    return this.toPlaylist(playlist)
  }
  async deletePlaylist(userId: string, id: string) {
    await this.requirePlaylist(userId, id)
    await this.repository.deletePlaylist(userId, id)
  }
  async setPlaylistTrack(userId: string, id: string, trackId: string, added: boolean) {
    const generated = added ? this.isGeneratedTrack(trackId) : false
    const outcome = await this.repository.setPlaylistTrack(userId, id, trackId, added, generated)
    if (outcome === 'playlist-not-found') throw playlistNotFound()
    if (outcome === 'track-not-found') throw trackNotFound()
  }
  async upload(userId: string, contentType: string, data: Buffer) {
    if (!audioTypes.has(contentType))
      throw new ApiError(415, 'UNSUPPORTED_AUDIO', 'Send an audio file as the raw request body')
    if (data.length === 0) throw new ApiError(400, 'VALIDATION', 'The audio file is empty')
    if (data.length > 20 * 1024 * 1024)
      throw new ApiError(413, 'PAYLOAD_TOO_LARGE', 'The audio file exceeds 20 MB')
    return {
      ...(await this.repository.saveUpload(userId, contentType, data)),
      durationMs: null,
      provider: 'mock',
    }
  }
  private isGeneratedTrack(id: string): boolean {
    if (catalogTracks.some((track) => track.id === id)) return false
    if (!z.uuid().safeParse(id).success) throw trackNotFound()
    return true
  }
  private async requirePlaylist(userId: string, id: string) {
    const playlist = await this.repository.findPlaylist(userId, id)
    if (!playlist) throw playlistNotFound()
    return playlist
  }
  private async toPlaylist(playlist: typeof playlists.$inferSelect) {
    return {
      id: playlist.id,
      name: playlist.name,
      createdAt: playlist.createdAt.toISOString(),
      trackIds: await this.repository.playlistTrackIds(playlist.id),
    }
  }
}
const playlistNotFound = () => new ApiError(404, 'PLAYLIST_NOT_FOUND', 'No such playlist')

const trackNotFound = () => new ApiError(404, 'TRACK_NOT_FOUND', 'No such track')
