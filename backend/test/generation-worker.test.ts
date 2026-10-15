import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { DatabaseHandle } from '../src/core/db/database.js'
import { LocalAudioStorage } from '../src/core/storage/audio-storage.js'
import { GenerationWorker } from '../src/features/tracks/generation/generation-worker.js'
import {
  type ComposedSong,
  type ComposeRequest,
  type MusicProvider,
  ProviderError,
} from '../src/features/tracks/generation/music-provider.js'
import { silentMp3 } from '../src/features/tracks/generation/silent-mp3.js'
import { TracksRepository } from '../src/features/tracks/tracks.repository.js'
import type { Track } from '../src/features/tracks/tracks.table.js'
import { UsersRepository } from '../src/features/users/users.repository.js'
import { createTestDatabase, truncateAll } from './support/database.js'
import { silentLogger } from './support/logger.js'

/** A provider whose every answer the test decides, one call at a time. */
class ScriptedProvider implements MusicProvider {
  readonly name = 'scripted'
  readonly requests: ComposeRequest[] = []

  constructor(private readonly script: ((signal: AbortSignal) => Promise<ComposedSong>)[]) {}

  compose(request: ComposeRequest, signal: AbortSignal): Promise<ComposedSong> {
    this.requests.push(request)
    const next = this.script.shift()
    if (!next) throw new Error('No more scripted answers')
    return next(signal)
  }
}

const song = (title: string): ComposedSong => ({
  audio: silentMp3(1_000),
  title,
  durationMs: 1_000,
  genres: ['pop'],
  explicit: false,
  words: [],
  songId: 'song-1',
})

const succeed = (title: string) => async () => song(title)
const failWith = (error: Error) => async (): Promise<never> => {
  throw error
}
const hang = (signal: AbortSignal) =>
  new Promise<never>((_, reject) => signal.addEventListener('abort', () => reject(signal.reason)))

let database: DatabaseHandle
let tracks: TracksRepository
let userId: string

beforeAll(async () => {
  database = await createTestDatabase()
  tracks = new TracksRepository(database.db)
})

beforeEach(async () => {
  await truncateAll(database.db)
  const user = await new UsersRepository(database.db).insertOrGet(
    { firebaseUid: 'dev-alice', email: null, emailVerified: true, signInProvider: 'dev' },
    new Date(),
  )
  userId = user.id
})

afterAll(() => database.close())

let keys = 0
async function queue(prompt = 'A calm piano piece for a rainy day'): Promise<Track> {
  const outcome = await tracks.create(
    {
      userId,
      idempotencyKey: `key-${++keys}`,
      prompt,
      lengthMs: null,
      instrumental: false,
      modelId: 'music_v2_5',
    },
    { since: new Date(0), max: 100 },
    new Date(Date.now() + keys),
  )
  if (outcome.kind !== 'created') throw new Error(`Unexpected ${outcome.kind}`)
  return outcome.track
}

async function workerWith(provider: MusicProvider, timeoutMs = 2_000, repository = tracks) {
  const storage = new LocalAudioStorage(await mkdtemp(join(tmpdir(), 'studio-worker-')))
  const worker = new GenerationWorker(
    { repository, provider, storage, log: silentLogger },
    { concurrency: 2, timeoutMs, retryDelaysMs: [1, 1], pollIntervalMs: 20 },
  )
  return { worker, storage }
}

const reload = async (track: Track) => tracks.findForUser(userId, track.id)

describe('GenerationWorker', () => {
  it('says so when there is nothing to do', async () => {
    const { worker } = await workerWith(new ScriptedProvider([]))

    expect(await worker.runOnce()).toBe(false)
  })

  it('does not call a provider for a queued track deleted before processing', async () => {
    const provider = new ScriptedProvider([])
    const { worker } = await workerWith(provider)
    const track = await queue()
    await tracks.softDeleteForUser(userId, track.id, new Date())
    expect(await worker.runOnce()).toBe(false)
    expect(provider.requests).toEqual([])
  })

  it('takes the oldest queued track first and passes on what was asked for', async () => {
    const provider = new ScriptedProvider([succeed('First'), succeed('Second')])
    const { worker } = await workerWith(provider)
    const first = await queue('The first song in the queue')
    await queue('The second song in the queue')

    await worker.runOnce()

    expect(provider.requests[0]).toEqual({
      prompt: 'The first song in the queue',
      variantIndex: 0,
      quality: 'auto',
      lengthMs: undefined,
      instrumental: false,
      modelId: 'music_v2_5',
    })
    expect(await reload(first)).toMatchObject({ status: 'done', title: 'First', songId: 'song-1' })
  })

  it('never gives the same track to two workers', async () => {
    const provider = new ScriptedProvider([succeed('A'), succeed('B')])
    const { worker } = await workerWith(provider)
    const a = await queue()
    const b = await queue()

    await Promise.all([worker.runOnce(), worker.runOnce()])

    expect(provider.requests).toHaveLength(2)
    expect((await reload(a))?.status).toBe('done')
    expect((await reload(b))?.status).toBe('done')
  })

  it('retries a rate-limited provider and succeeds', async () => {
    const busy = new ProviderError('RATE_LIMITED', 'Too many requests', true)
    const provider = new ScriptedProvider([failWith(busy), failWith(busy), succeed('Patience')])
    const { worker } = await workerWith(provider)
    const track = await queue()

    await worker.runOnce()

    expect(provider.requests).toHaveLength(3)
    expect((await reload(track))?.status).toBe('done')
  })

  it('does not retry a rejected prompt and keeps the suggestion', async () => {
    const rejected = new ProviderError('INVALID_PROMPT', 'Names an artist', false, 'Try this')
    const provider = new ScriptedProvider([failWith(rejected)])
    const { worker } = await workerWith(provider)
    const track = await queue()

    await worker.runOnce()

    expect(provider.requests).toHaveLength(1)
    expect(await reload(track)).toMatchObject({
      status: 'failed',
      errorCode: 'INVALID_PROMPT',
      promptSuggestion: 'Try this',
    })
  })

  it('gives up when the provider takes too long', async () => {
    const { worker } = await workerWith(new ScriptedProvider([hang]), 50)
    const track = await queue()

    await worker.runOnce()

    expect(await reload(track)).toMatchObject({ status: 'failed', errorCode: 'TIMEOUT' })
  })

  it('reports an unexpected error without its details', async () => {
    const provider = new ScriptedProvider([failWith(new Error('socket hang up at 10.0.0.3'))])
    const { worker } = await workerWith(provider)
    const track = await queue()

    await worker.runOnce()

    expect(await reload(track)).toMatchObject({
      status: 'failed',
      errorCode: 'INTERNAL',
      errorMessage: 'The generation failed unexpectedly',
    })
  })

  it('throws the audio away when the track was deleted during the generation', async () => {
    const track = await queue()
    const provider = new ScriptedProvider([
      async () => {
        await tracks.softDeleteForUser(userId, track.id, new Date())
        return song('Too late')
      },
    ])
    const { worker, storage } = await workerWith(provider)

    await worker.runOnce()

    expect(await storage.open(track.id)).toBeUndefined()
  })

  it('fails the track and removes the file when the result cannot be saved', async () => {
    class LostConnection extends TracksRepository {
      override async complete(): Promise<boolean> {
        throw new Error('Connection terminated unexpectedly')
      }
    }
    const { worker, storage } = await workerWith(
      new ScriptedProvider([succeed('Paid for')]),
      2_000,
      new LostConnection(database.db),
    )
    const track = await queue()

    await worker.runOnce()

    expect(await reload(track)).toMatchObject({
      status: 'failed',
      errorCode: 'INTERNAL',
      errorMessage: 'The song was generated but could not be saved',
    })
    expect(await storage.open(track.id)).toBeUndefined()
  })

  it('puts the song in progress back in the queue when the server stops', async () => {
    const { worker } = await workerWith(new ScriptedProvider([hang]))
    const track = await queue()
    worker.start()
    worker.wake()
    await expect.poll(async () => (await reload(track))?.status, { timeout: 2_000 }).toBe('running')

    await worker.stop()

    expect(await reload(track)).toMatchObject({ status: 'queued', startedAt: null })
  })

  it('picks up new work right away once started, and stops cleanly', async () => {
    const { worker } = await workerWith(new ScriptedProvider([succeed('Live')]))
    worker.start()
    const track = await queue()
    worker.wake()

    await expect.poll(async () => (await reload(track))?.status, { timeout: 2_000 }).toBe('done')
    await worker.stop()
  })
})

describe('TracksRepository.failAbandoned', () => {
  it('fails tracks that have been running far longer than any generation can take', async () => {
    const stuck = await queue()
    const fresh = await queue()
    await tracks.claimNext(new Date('2026-10-14T10:00:00Z'))
    await tracks.claimNext(new Date('2026-10-14T12:00:00Z'))

    const count = await tracks.failAbandoned(
      new Date('2026-10-14T11:00:00Z'),
      { code: 'INTERRUPTED', message: 'Restarted', promptSuggestion: null },
      new Date('2026-10-14T12:01:00Z'),
    )

    expect(count).toBe(1)
    expect((await reload(stuck))?.errorCode).toBe('INTERRUPTED')
    expect((await reload(fresh))?.status).toBe('running')
  })
})
