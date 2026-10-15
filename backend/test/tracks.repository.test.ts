import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { LibraryRepository } from '../src/features/library/library.repository.js'
import { LibraryService } from '../src/features/library/library.service.js'
import { TracksRepository } from '../src/features/tracks/tracks.repository.js'
import type { Track } from '../src/features/tracks/tracks.table.js'
import { UsersRepository } from '../src/features/users/users.repository.js'
import { createTestDatabase, truncateAll } from './support/database.js'

let database: DatabaseHandle
let tracks: TracksRepository
let userId: string
const today = new Date('2026-10-14T00:00:00Z')

beforeAll(async () => {
  database = await createTestDatabase()
  tracks = new TracksRepository(database.db)
})

beforeEach(async () => {
  await truncateAll(database.db)
  userId = (
    await new UsersRepository(database.db).insertOrGet(
      { firebaseUid: 'dev-alice', email: null, emailVerified: true, signInProvider: 'dev' },
      today,
    )
  ).id
})

afterAll(() => database.close())

let keys = 0
async function create(at = new Date('2026-10-14T09:00:00Z')): Promise<Track> {
  const outcome = await tracks.create(
    {
      userId,
      idempotencyKey: `key-${++keys}`,
      prompt: 'Lo-fi beats for a long night of studying',
      lengthMs: null,
      instrumental: true,
      modelId: 'music_v2_5',
    },
    { since: today, max: 100 },
    at,
  )
  if (outcome.kind !== 'created') throw new Error(`Unexpected ${outcome.kind}`)
  return outcome.track
}

async function failWith(code: string): Promise<void> {
  await create()
  const claimed = await tracks.claimNext(new Date())
  if (!claimed) throw new Error('Nothing to claim')
  await tracks.fail(claimed.id, { code, message: code, promptSuggestion: null }, new Date())
}

describe('the daily limit', () => {
  it('blocks generation after account deletion has been requested', async () => {
    await new UsersRepository(database.db).requestDeletion(userId, new Date())
    const outcome = await tracks.create(
      {
        userId,
        idempotencyKey: 'blocked-deletion',
        prompt: 'A generation must not recreate account audio',
        lengthMs: null,
        instrumental: true,
        modelId: 'music_v2_5',
      },
      { since: today, max: 100 },
      new Date(),
    )
    expect(outcome.kind).toBe('account-deleting')
    expect(await tracks.listIdsForUser(userId)).toEqual([])
  })
  it('counts queued, running and finished tracks of today only', async () => {
    await create()
    await create()
    await tracks.claimNext(new Date())
    await create(new Date('2026-10-13T23:59:59Z'))

    expect(await tracks.countTowardLimit(userId, today)).toBe(2)
  })

  it.each(['TIMEOUT', 'INTERRUPTED', 'INTERNAL'])(
    'counts a %s failure, since the song may have been paid for',
    async (code) => {
      await failWith(code)

      expect(await tracks.countTowardLimit(userId, today)).toBe(1)
    },
  )

  it.each(['INVALID_PROMPT', 'QUOTA_EXCEEDED', 'RATE_LIMITED', 'PROVIDER_UNAVAILABLE'])(
    'does not count a %s failure, which the provider refused before generating',
    async (code) => {
      await failWith(code)

      expect(await tracks.countTowardLimit(userId, today)).toBe(0)
    },
  )
})

describe('deleted jobs and library references', () => {
  it('does not requeue a running track after deletion', async () => {
    const track = await create()
    await tracks.claimNext(new Date())
    await tracks.softDeleteForUser(userId, track.id, new Date())
    expect(await tracks.requeue(track.id, new Date())).toBe(false)
    expect(await tracks.claimNext(new Date())).toBeUndefined()
  })
  it('never leaves a like after racing addition with track deletion', async () => {
    const library = new LibraryService(new LibraryRepository(database.db))
    const track = await create()
    const outcomes = await Promise.allSettled([
      library.setLiked(userId, track.id, true),
      tracks.softDeleteForUser(userId, track.id, new Date()),
    ])
    for (const outcome of outcomes)
      if (outcome.status === 'rejected') expect(outcome.reason.code).toBe('TRACK_NOT_FOUND')
    expect(await library.likedIds(userId)).toEqual([])
    await expect(library.setLiked(userId, track.id, true)).rejects.toMatchObject({
      code: 'TRACK_NOT_FOUND',
    })
  })
  it('never leaves a playlist member after racing addition with track deletion', async () => {
    const library = new LibraryService(new LibraryRepository(database.db))
    const playlist = await library.createPlaylist(userId, 'Race playlist')
    const track = await create()
    const outcomes = await Promise.allSettled([
      library.setPlaylistTrack(userId, playlist.id, track.id, true),
      tracks.softDeleteForUser(userId, track.id, new Date()),
    ])
    for (const outcome of outcomes)
      if (outcome.status === 'rejected') expect(outcome.reason.code).toBe('TRACK_NOT_FOUND')
    expect((await library.playlist(userId, playlist.id)).trackIds).toEqual([])
    await expect(
      library.setPlaylistTrack(userId, playlist.id, track.id, true),
    ).rejects.toMatchObject({ code: 'TRACK_NOT_FOUND' })
  })
})

describe('softDeleteForUser', () => {
  it('wipes the content and hides the track, but keeps it for the limit', async () => {
    const track = await create()

    expect(await tracks.softDeleteForUser(userId, track.id, new Date())).toBe(true)

    expect(await tracks.findForUser(userId, track.id)).toBeUndefined()
    expect(await tracks.listForUser(userId, 10)).toEqual([])
    expect(await tracks.countTowardLimit(userId, today)).toBe(1)
    expect(await tracks.listIdsForUser(userId)).toEqual([track.id])
  })

  it('reports a second delete as not found', async () => {
    const track = await create()
    await tracks.softDeleteForUser(userId, track.id, new Date())

    expect(await tracks.softDeleteForUser(userId, track.id, new Date())).toBe(false)
  })

  it('makes a generation that finishes later throw its result away', async () => {
    const track = await create()
    await tracks.claimNext(new Date())
    await tracks.softDeleteForUser(userId, track.id, new Date())

    const saved = await tracks.complete(
      track.id,
      { title: 'Late', durationMs: 1, genres: [], explicit: false, words: [], songId: null },
      new Date(),
    )

    expect(saved).toBe(false)
  })
})
