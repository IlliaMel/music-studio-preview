import { describe, expect, it } from 'vitest'
import { ConfigError, loadConfig } from '../src/core/config.js'

// Assembled from parts so secret scanners do not mistake this placeholder for a leaked key.
const pem = (label: string) => `-----${label} ${'PRIVATE'} KEY-----`
const fakePrivateKey = `${pem('BEGIN')}\nnot-a-real-key\n${pem('END')}\n`

const serviceAccount = JSON.stringify({
  type: 'service_account',
  project_id: 'studio-test',
  client_email: 'backend@studio-test.iam.gserviceaccount.com',
  private_key: fakePrivateKey,
})

describe('loadConfig', () => {
  it('runs locally with no configuration at all', () => {
    expect(loadConfig({})).toEqual({
      env: 'development',
      server: { host: '127.0.0.1', port: 8787, publicBaseUrl: undefined },
      log: { level: 'info', pretty: false },
      database: { kind: 'pglite', dataDir: './data/pglite' },
      auth: { mode: 'dev' },
      generation: {
        provider: 'mock',
        mockDelayMs: 8_000,
        concurrency: 2,
        timeoutMs: 300_000,
        dailyLimit: 25,
      },
      storage: { audioDir: './data/audio' },
    })
  })

  it('treats an empty value in .env as not set', () => {
    const config = loadConfig({ PORT: '', DATABASE_URL: '', LOG_PRETTY: '', AUTH_MODE: '' })

    expect(config.server.port).toBe(8787)
    expect(config.database.kind).toBe('pglite')
    expect(config.log.pretty).toBe(false)
    expect(config.auth.mode).toBe('dev')
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
})

describe('loadConfig in production', () => {
  it('needs a real database, persistent audio, its public address and Firebase', () => {
    const error = captureError(() => loadConfig({ NODE_ENV: 'production' })) as ConfigError

    expect(error.problems.map((problem) => problem.split(':')[0])).toEqual([
      'DATABASE_URL',
      'PUBLIC_BASE_URL',
      'AUDIO_DIR',
      'FIREBASE_SERVICE_ACCOUNT',
    ])
  })

  it('starts with both', () => {
    const config = loadConfig({
      NODE_ENV: 'production',
      HOST: '0.0.0.0',
      DATABASE_URL: 'postgres://db.internal/studio',
      PUBLIC_BASE_URL: 'https://api.studio.example/',
      AUDIO_DIR: '/data/audio',
      FIREBASE_SERVICE_ACCOUNT: serviceAccount,
    })

    expect(config.server.publicBaseUrl).toBe('https://api.studio.example')
    expect(config.storage.audioDir).toBe('/data/audio')

    expect(config.auth).toEqual({
      mode: 'firebase',
      serviceAccount: {
        projectId: 'studio-test',
        clientEmail: 'backend@studio-test.iam.gserviceaccount.com',
        privateKey: fakePrivateKey,
      },
    })
  })
})

describe('dev sign-in', () => {
  it('is refused in production even when asked for', () => {
    expect(() =>
      loadConfig({
        NODE_ENV: 'production',
        AUTH_MODE: 'dev',
        DATABASE_URL: 'postgres://db.internal/studio',
      }),
    ).toThrow(/AUTH_MODE: dev sign-in/)
  })

  it('is refused when the server listens beyond this computer', () => {
    expect(() => loadConfig({ HOST: '0.0.0.0' })).toThrow(/AUTH_MODE: dev sign-in/)
  })

  it.each(['127.0.0.1', 'localhost', '::1'])('is allowed on %s', (host) => {
    expect(loadConfig({ HOST: host }).auth.mode).toBe('dev')
  })
})

describe('FIREBASE_SERVICE_ACCOUNT', () => {
  it('is required for Firebase sign-in', () => {
    expect(() => loadConfig({ AUTH_MODE: 'firebase' })).toThrow(
      /FIREBASE_SERVICE_ACCOUNT: required/,
    )
  })

  it('must be the JSON key file', () => {
    expect(() =>
      loadConfig({ AUTH_MODE: 'firebase', FIREBASE_SERVICE_ACCOUNT: 'service-account.json' }),
    ).toThrow(/FIREBASE_SERVICE_ACCOUNT: must be the JSON key file/)
  })

  it('must contain a private key', () => {
    const withoutKey = JSON.stringify({ project_id: 'studio-test', client_email: 'a@b.co' })

    expect(() =>
      loadConfig({ AUTH_MODE: 'firebase', FIREBASE_SERVICE_ACCOUNT: withoutKey }),
    ).toThrow(/FIREBASE_SERVICE_ACCOUNT\.private_key/)
  })

  it('refuses pretty logs in production, where pino-pretty is not installed', () => {
    expect(() =>
      loadConfig({
        NODE_ENV: 'production',
        DATABASE_URL: 'postgres://db.internal/studio',
        LOG_PRETTY: 'true',
      }),
    ).toThrow(/LOG_PRETTY/)
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
