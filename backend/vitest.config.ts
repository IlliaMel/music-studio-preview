import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['test/**/*.test.ts'],
    // Each test file starts an embedded Postgres (PGlite) and runs the migrations.
    testTimeout: 20_000,
    hookTimeout: 30_000,
  },
})
