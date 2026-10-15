import { getTableName, is, sql } from 'drizzle-orm'
import { PgTable } from 'drizzle-orm/pg-core'
import { type Database, type DatabaseHandle, openDatabase } from '../../src/core/db/database.js'
import * as schema from '../../src/core/db/schema.js'

/** A fresh in-memory Postgres (PGlite) with every migration applied. */
export async function createTestDatabase(): Promise<DatabaseHandle> {
  const database = await openDatabase({ kind: 'pglite' })
  await database.migrate()
  return database
}

/** Empties every table between tests: much faster than starting a new database each time. */
export async function truncateAll(db: Database): Promise<void> {
  const tables = Object.values(schema)
    .filter((value) => is(value, PgTable))
    .map((table) => `"${getTableName(table)}"`)
  if (tables.length > 0) {
    await db.execute(sql.raw(`truncate ${tables.join(', ')} restart identity cascade`))
  }
}
