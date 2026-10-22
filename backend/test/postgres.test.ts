import { PGlite } from '@electric-sql/pglite'
import { PGLiteSocketServer } from '@electric-sql/pglite-socket'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { createApp } from '../src/app.js'
import { openDatabase } from '../src/core/db/database.js'
import { createLogger } from '../src/core/logger.js'
import { UsersRepository } from '../src/features/users/users.repository.js'

// The production path (the `pg` driver over the network, migrations under an advisory lock)
// tested without Docker: PGlite serves the Postgres wire protocol on a local port.
let pglite: PGlite
let server: PGLiteSocketServer
let url: string

beforeAll(async () => {
  pglite = new PGlite()
  server = new PGLiteSocketServer({ db: pglite, host: '127.0.0.1', port: 0, maxConnections: 10 })
  await server.start()
  url = `postgresql://postgres@${server.getServerConn()}/postgres`
})

afterAll(async () => {
  await server.stop()
  await pglite.close()
})

describe('openDatabase with a Postgres connection string', () => {
  it('migrates over the network and serves queries through the pool', async () => {
    const database = await openDatabase({ kind: 'postgres', url })
    await database.migrate()

    const user = await new UsersRepository(database.db).insertOrGet(
      {
        firebaseUid: 'firebase-alice',
        email: null,
        emailVerified: false,
        signInProvider: 'apple.com',
      },
      new Date('2026-10-14T12:00:00Z'),
    )
    await database.close()

    expect(user.signInProvider).toBe('apple.com')
  })

  it('finds nothing to do when the migrations already ran', async () => {
    const database = await openDatabase({ kind: 'postgres', url })

    await expect(database.migrate()).resolves.toBeUndefined()
    await database.close()
  })

  it('survives an idle connection loss, reports unhealthy, and reconnects without losing data', async () => {
    const lines: string[] = []
    const log = createLogger(
      { level: 'error', pretty: false },
      { write: (line) => lines.push(line) },
    )
    const database = await openDatabase({ kind: 'postgres', url }, log)
    const app = createApp({
      db: database.db,
      log,
      authenticate: async (_c, next) => next(),
      features: [],
    })
    let stopped = false
    try {
      await database.migrate()
      const users = new UsersRepository(database.db)
      const user = await users.insertOrGet(
        {
          firebaseUid: 'survives-disconnect',
          email: null,
          emailVerified: false,
          signInProvider: 'google.com',
        },
        new Date(),
      )
      expect((await app.request('/health')).status).toBe(200)

      // No query is in flight: this closes the actual network connection while it is idle
      // in pg.Pool. Without the pool error listener Node emits an uncaught exception.
      await server.stop()
      stopped = true
      await expect
        .poll(() => lines.some((line) => line.includes('idle database connection lost')))
        .toBe(true)
      const idleError = JSON.parse(
        lines.find((line) => line.includes('idle database connection lost')) ?? '{}',
      )
      expect(idleError.databaseError).toEqual({ type: 'Error' })
      expect(idleError).not.toHaveProperty('client')
      expect(JSON.stringify(idleError)).not.toContain(url)
      expect((await app.request('/health')).status).toBe(503)

      await server.start()
      stopped = false
      expect((await app.request('/health')).status).toBe(200)
      expect((await users.findByFirebaseUid('survives-disconnect'))?.id).toBe(user.id)
    } finally {
      await database.close()
      if (stopped) await server.start()
    }
  })
})
