import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { PGlite } from '@electric-sql/pglite'
import { drizzle as drizzleNodePg } from 'drizzle-orm/node-postgres'
import { migrate as migrateNodePg } from 'drizzle-orm/node-postgres/migrator'
import type { PgDatabase, PgQueryResultHKT } from 'drizzle-orm/pg-core'
import { drizzle as drizzlePglite } from 'drizzle-orm/pglite'
import { migrate as migratePglite } from 'drizzle-orm/pglite/migrator'
import pg from 'pg'
import type { DatabaseConfig } from '../config.js'
import * as schema from './schema.js'

export type Schema = typeof schema

/** The one database type the rest of the code sees, whichever driver is underneath. */
export type Database = PgDatabase<PgQueryResultHKT, Schema>

export type DatabaseHandle = {
  db: Database
  /** Applies the SQL migrations from `drizzle/` that have not run yet. */
  migrate(): Promise<void>
  close(): Promise<void>
}

// `src/core/db` and `dist/core/db` are both three levels below the backend root.
const migrationsFolder = fileURLToPath(new URL('../../../drizzle', import.meta.url))

// Any fixed number works, as long as every instance of the server uses the same one.
const MIGRATION_LOCK_ID = 7_134_202_610

/**
 * Two instances starting together (a rolling deploy) would both try to apply the same migration.
 * A Postgres advisory lock lets one apply it while the other waits, then finds nothing to do.
 * The lock belongs to one connection, so lock, migrate and unlock all use the same client.
 */
async function migrateUnderLock(pool: pg.Pool): Promise<void> {
  const client = await pool.connect()
  try {
    await client.query('select pg_advisory_lock($1)', [MIGRATION_LOCK_ID])
    await migrateNodePg(drizzleNodePg({ client, schema }), { migrationsFolder })
  } finally {
    // Closing the connection would release the lock too; unlocking just makes it immediate.
    await client.query('select pg_advisory_unlock($1)', [MIGRATION_LOCK_ID]).catch(() => {})
    client.release()
  }
}

export async function openDatabase(config: DatabaseConfig): Promise<DatabaseHandle> {
  if (config.kind === 'postgres') {
    const pool = new pg.Pool({ connectionString: config.url, max: 10 })
    return {
      db: drizzleNodePg({ client: pool, schema }),
      migrate: () => migrateUnderLock(pool),
      close: () => pool.end(),
    }
  }

  // No data directory means an in-memory database that disappears with the process (tests).
  // PGlite creates only the last folder of the path, so a fresh clone needs the parents made.
  if (config.dataDir) await mkdir(config.dataDir, { recursive: true })
  const client = config.dataDir ? new PGlite(config.dataDir) : new PGlite()
  await client.waitReady
  const db = drizzlePglite({ client, schema })
  return {
    db,
    migrate: () => migratePglite(db, { migrationsFolder }),
    close: () => client.close(),
  }
}
