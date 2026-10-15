import { PGlite } from '@electric-sql/pglite'
import { PGLiteSocketServer } from '@electric-sql/pglite-socket'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { openDatabase } from '../src/core/db/database.js'
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
})
