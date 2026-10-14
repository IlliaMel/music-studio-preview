import { describe, expect, it } from 'vitest'
import { ConfigError, loadConfig } from '../src/core/config.js'

describe('loadConfig', () => {
  it('runs locally with no configuration at all', () => {
    expect(loadConfig({})).toEqual({
      env: 'development',
      server: { host: '127.0.0.1', port: 8787 },
      log: { level: 'info', pretty: false },
      database: { kind: 'pglite', dataDir: './data/pglite' },
    })
  })

  it('treats an empty value in .env as not set', () => {
    const config = loadConfig({ PORT: '', DATABASE_URL: '', LOG_PRETTY: '' })

    expect(config.server.port).toBe(8787)
    expect(config.database.kind).toBe('pglite')
    expect(config.log.pretty).toBe(false)
  })

  it('uses Postgres when a connection string is given', () => {
    const config = loadConfig({
      DATABASE_URL: 'postgresql://studio:secret@db.internal:5432/studio',
    })

    expect(config.database).toEqual({
      kind: 'postgres',
      url: 'postgresql://studio:secret@db.internal:5432/studio',
    })
  })

  it('names every invalid variable', () => {
    const error = captureError(() =>
      loadConfig({ PORT: 'eighty', DATABASE_URL: 'mysql://db/studio', LOG_PRETTY: 'yes' }),
    )

    expect(error).toBeInstanceOf(ConfigError)
    expect((error as ConfigError).problems.map((problem) => problem.split(':')[0])).toEqual([
      'PORT',
      'LOG_PRETTY',
      'DATABASE_URL',
    ])
  })

  it('refuses to run in production on the embedded database', () => {
    expect(() => loadConfig({ NODE_ENV: 'production' })).toThrow(/DATABASE_URL: required/)
  })
})

function captureError(block: () => unknown): unknown {
  try {
    block()
  } catch (error) {
    return error
  }
  throw new Error('Expected an error')
}
