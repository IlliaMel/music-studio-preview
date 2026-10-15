import { defineConfig } from 'drizzle-kit'

// Generates SQL migrations from the schema: `npm run db:generate`.
// The server applies them itself on start, so drizzle-kit never connects to a database.
export default defineConfig({
  dialect: 'postgresql',
  schema: './src/core/db/schema.ts',
  out: './drizzle',
  strict: true,
})
