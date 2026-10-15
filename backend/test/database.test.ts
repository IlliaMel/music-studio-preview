import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import { openDatabase } from '../src/core/db/database.js'
import { UsersRepository } from '../src/features/users/users.repository.js'

const tempDirs: string[] = []

afterEach(async () => {
  await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })))
})

describe('openDatabase with PGlite on disk', () => {
  it('creates missing parent folders, as on a fresh clone', async () => {
    const root = await mkdtemp(join(tmpdir(), 'studio-db-'))
    tempDirs.push(root)

    const database = await openDatabase({ kind: 'pglite', dataDir: join(root, 'data', 'pglite') })
    await database.migrate()

    await expect(
      new UsersRepository(database.db).findByFirebaseUid('nobody'),
    ).resolves.toBeUndefined()
    await database.close()
  })

  it('keeps data between restarts and does not re-run migrations', async () => {
    const root = await mkdtemp(join(tmpdir(), 'studio-db-'))
    tempDirs.push(root)
    const config = { kind: 'pglite', dataDir: join(root, 'pglite') } as const

    const first = await openDatabase(config)
    await first.migrate()
    const created = await new UsersRepository(first.db).insertOrGet(
      {
        firebaseUid: 'firebase-alice',
        email: null,
        emailVerified: false,
        signInProvider: 'google.com',
      },
      new Date(),
    )
    await first.close()

    const second = await openDatabase(config)
    await second.migrate()
    const found = await new UsersRepository(second.db).findByFirebaseUid('firebase-alice')
    await second.close()

    expect(found?.id).toBe(created.id)
  })
})
