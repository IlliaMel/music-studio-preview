import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv } from '../../core/http/app-env.js'
import { ApiError } from '../../core/http/errors.js'
import { fileResponse } from '../../core/http/file-response.js'
import { validate } from '../../core/http/validation.js'
import { demoAudioFile } from '../tracks/generation/demo-audio.js'
import { catalogMetadata, catalogTracks, collections, stations } from './catalog.js'
import type { LibraryService } from './library.service.js'

const PlaylistParams = z.object({ id: z.uuid() })
const TrackParams = z.object({ trackId: z.string().min(1).max(100) })
const PlaylistTrackParams = PlaylistParams.extend({ trackId: z.string().min(1).max(100) })
const PlaylistBody = z.strictObject({ name: z.string().trim().min(1).max(80) })
export const libraryRoutes = (service: LibraryService, publicBaseUrl?: string) =>
  new Hono<AppEnv>()
    .get('/catalog', (c) =>
      c.json({
        ...catalogMetadata,
        collections,
        stations,
        tracks: catalogTracks.map((track) => ({
          ...track,
          audioUrl: new URL(`/v1/catalog/tracks/${track.id}/audio`, publicBaseUrl ?? c.req.url)
            .href,
          provider: 'fixture',
        })),
      }),
    )
    .get('/trends', (c) => c.json(catalogMetadata))
    .get('/catalog/tracks/:id/audio', (c) => {
      const track = catalogTracks.find((item) => item.id === c.req.param('id'))
      if (!track) throw new ApiError(404, 'TRACK_NOT_FOUND', 'No such catalog track')
      return fileResponse(demoAudioFile(track.durationMs), 'audio/wav', {
        method: c.req.method,
        range: c.req.header('Range'),
      })
    })
    .get('/collections/:id', (c) => {
      const collection = collections.find((item) => item.id === c.req.param('id'))
      if (!collection) throw new ApiError(404, 'COLLECTION_NOT_FOUND', 'No such collection')
      return c.json({
        ...collection,
        tracks: collection.trackIds.flatMap(
          (id) => catalogTracks.find((track) => track.id === id) ?? [],
        ),
      })
    })
    .get('/stations/:id', (c) => {
      const station = stations.find((item) => item.id === c.req.param('id'))
      if (!station) throw new ApiError(404, 'STATION_NOT_FOUND', 'No such station')
      return c.json({
        ...station,
        tracks: station.trackIds.flatMap(
          (id) => catalogTracks.find((track) => track.id === id) ?? [],
        ),
      })
    })

    .get('/likes', async (c) => c.json({ trackIds: await service.likedIds(c.get('user').id) }))
    .put('/likes/:trackId', validate('param', TrackParams), async (c) => {
      await service.setLiked(c.get('user').id, c.req.valid('param').trackId, true)
      return c.body(null, 204)
    })
    .delete('/likes/:trackId', validate('param', TrackParams), async (c) => {
      await service.setLiked(c.get('user').id, c.req.valid('param').trackId, false)
      return c.body(null, 204)
    })
    .get('/playlists', async (c) =>
      c.json({ playlists: await service.listPlaylists(c.get('user').id) }),
    )
    .post('/playlists', validate('json', PlaylistBody), async (c) =>
      c.json(await service.createPlaylist(c.get('user').id, c.req.valid('json').name), 201),
    )
    .get('/playlists/:id', validate('param', PlaylistParams), async (c) =>
      c.json(await service.playlist(c.get('user').id, c.req.valid('param').id)),
    )
    .patch(
      '/playlists/:id',
      validate('param', PlaylistParams),
      validate('json', PlaylistBody),
      async (c) =>
        c.json(
          await service.renamePlaylist(
            c.get('user').id,
            c.req.valid('param').id,
            c.req.valid('json').name,
          ),
        ),
    )
    .delete('/playlists/:id', validate('param', PlaylistParams), async (c) => {
      await service.deletePlaylist(c.get('user').id, c.req.valid('param').id)
      return c.body(null, 204)
    })
    .put('/playlists/:id/tracks/:trackId', validate('param', PlaylistTrackParams), async (c) => {
      const { id, trackId } = c.req.valid('param')
      await service.setPlaylistTrack(c.get('user').id, id, trackId, true)
      return c.body(null, 204)
    })
    .delete('/playlists/:id/tracks/:trackId', validate('param', PlaylistTrackParams), async (c) => {
      const { id, trackId } = c.req.valid('param')
      await service.setPlaylistTrack(c.get('user').id, id, trackId, false)
      return c.body(null, 204)
    })
    .delete('/uploads/:id', validate('param', PlaylistParams), async (c) => {
      await service.deleteUpload(c.get('user').id, c.req.valid('param').id)
      return c.body(null, 204)
    })
    .post('/uploads', async (c) =>
      c.json(
        await service.upload(
          c.get('user').id,
          c.req.header('Content-Type')?.split(';')[0]?.trim() ?? '',
          Buffer.from(await c.req.arrayBuffer()),
        ),
        201,
      ),
    )
