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

export async function openDatabase(config: DatabaseConfig): Promise<DatabaseHandle> {
  if (config.kind === 'postgres') {
    const pool = new pg.Pool({ connectionString: config.url, max: 10 })
    const db = drizzleNodePg({ client: pool, schema })
    return {
      db,
      migrate: () => migrateNodePg(db, { migrationsFolder }),
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
