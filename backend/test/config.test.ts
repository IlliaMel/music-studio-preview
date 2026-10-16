import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
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
      http: {
        requestsPerMinute: 1200,
        ownerRequestsPerMinute: 240,
        concurrentUploads: 4,
        ownerConcurrentUploads: 2,
      },
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
      'MUSIC_PROVIDER',
      'DATABASE_URL',
      'PUBLIC_BASE_URL',
      'AUDIO_DIR',
      'FIREBASE_SERVICE_ACCOUNT',
    ])
  })

  it('allows an explicitly selected mock for intentional production-like staging', () => {
    const config = loadConfig({
      NODE_ENV: 'production',
      HOST: '0.0.0.0',
      MUSIC_PROVIDER: 'mock',
      DATABASE_URL: 'postgres://db.internal/studio',
      PUBLIC_BASE_URL: 'https://api.studio.example/',
      AUDIO_DIR: '/data/audio',
      FIREBASE_SERVICE_ACCOUNT: serviceAccount,
    })

    expect(config.server.publicBaseUrl).toBe('https://api.studio.example')
    expect(config.storage.audioDir).toBe('/data/audio')
    expect(config.generation.provider).toBe('mock')

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

  it.each(['10.0.0.1', '10.255.255.254', '172.16.0.1', '172.31.255.254', '192.168.1.2'])(
    'allows explicit trusted-LAN development on %s, but keeps the default closed',
    (host) => {
      expect(() => loadConfig({ HOST: host })).toThrow(/AUTH_MODE: dev sign-in/)
      expect(() => loadConfig({ HOST: host, ALLOW_LAN_DEV_AUTH: 'false' })).toThrow(
        /AUTH_MODE: dev sign-in/,
      )
      expect(
        loadConfig({ NODE_ENV: 'development', HOST: host, ALLOW_LAN_DEV_AUTH: 'true' }).auth.mode,
      ).toBe('dev')
    },
  )

  it.each([
    '0.0.0.0',
    '8.8.8.8',
    '172.15.255.255',
    '172.32.0.1',
    '192.169.1.2',
    '169.254.1.2',
    '192.168.1.256',
    '10.-1.2.3',
    '10.1.2',
    '010.1.2.3',
    '10.1.2.3.example',
    'studio.local',
    '::',
    '::ffff:192.168.1.2',
  ])('refuses LAN opt-in on invalid or non-private HOST %s', (host) => {
    expect(() => loadConfig({ HOST: host, ALLOW_LAN_DEV_AUTH: 'true' })).toThrow(
      /ALLOW_LAN_DEV_AUTH: requires/,
    )
  })

  it.each(['test', 'production'])('refuses LAN opt-in in %s', (nodeEnv) => {
    expect(() =>
      loadConfig({
        NODE_ENV: nodeEnv,
        HOST: '192.168.1.2',
        AUTH_MODE: 'dev',
        ALLOW_LAN_DEV_AUTH: 'true',
      }),
    ).toThrow(/ALLOW_LAN_DEV_AUTH: requires/)
  })

  it('validates the opt-in boolean rather than accepting truthy strings', () => {
    expect(() => loadConfig({ ALLOW_LAN_DEV_AUTH: 'yes' })).toThrow(/ALLOW_LAN_DEV_AUTH/)
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

it('enables live music only with a key and explicitly validates provider/HTTPS', () => {
  expect(() => loadConfig({ MUSIC_PROVIDER: 'elevenlabs' })).toThrow(/ELEVENLABS_API_KEY/)
  expect(() => loadConfig({ MUSIC_PROVIDER: 'invented' })).toThrow(/MUSIC_PROVIDER/)
  expect(
    loadConfig({
      MUSIC_PROVIDER: 'elevenlabs',
      ELEVENLABS_API_KEY: 'test-placeholder',
      ELEVENLABS_ENABLE_REFERENCES: 'true',
    }).generation,
  ).toMatchObject({
    provider: 'elevenlabs',
    elevenlabs: { apiKey: 'test-placeholder', references: true },
  })
  expect(() =>
    loadConfig({
      NODE_ENV: 'production',
      DATABASE_URL: 'postgres://db/studio',
      AUDIO_DIR: '/persistent/audio',
      PUBLIC_BASE_URL: 'http://api.example.test',
      FIREBASE_SERVICE_ACCOUNT: serviceAccount,
    }),
  ).toThrow(/HTTPS/)
})

it('loads a backend Firebase credential file without exposing unreadable-file details', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'firebase-config-'))
  const file = join(directory, 'admin.json')
  try {
    await writeFile(file, serviceAccount)
    expect(
      loadConfig({ AUTH_MODE: 'firebase', FIREBASE_SERVICE_ACCOUNT_FILE: file }).auth.mode,
    ).toBe('firebase')
    expect(() =>
      loadConfig({
        AUTH_MODE: 'firebase',
        FIREBASE_SERVICE_ACCOUNT_FILE: join(directory, 'missing.json'),
      }),
    ).toThrow('FIREBASE_SERVICE_ACCOUNT_FILE: could not read credential file')
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

it('rejects an omitted production provider even when the other deployment inputs are valid', () => {
  expect(() =>
    loadConfig({
      NODE_ENV: 'production',
      DATABASE_URL: 'postgres://db/studio',
      PUBLIC_BASE_URL: 'https://api.example.test',
      AUDIO_DIR: '/data/audio',
      FIREBASE_SERVICE_ACCOUNT: serviceAccount,
    }),
  ).toThrow('MUSIC_PROVIDER: explicitly choose')
})
it('validates and applies HTTP resource limits', () => {
  expect(
    loadConfig({
      HTTP_REQUESTS_PER_MINUTE: '50',
      HTTP_OWNER_REQUESTS_PER_MINUTE: '10',
      HTTP_CONCURRENT_UPLOADS: '3',
      HTTP_OWNER_CONCURRENT_UPLOADS: '1',
    }).http,
  ).toEqual({
    requestsPerMinute: 50,
    ownerRequestsPerMinute: 10,
    concurrentUploads: 3,
    ownerConcurrentUploads: 1,
  })
  expect(() => loadConfig({ HTTP_CONCURRENT_UPLOADS: '0' })).toThrow('HTTP_CONCURRENT_UPLOADS')
})
