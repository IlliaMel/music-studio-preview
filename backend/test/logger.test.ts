import { DrizzleQueryError } from 'drizzle-orm/errors'
import { describe, expect, it } from 'vitest'
import { createLogger } from '../src/core/logger.js'
import { serializeError } from '../src/core/safe-error.js'

describe('private error logging', () => {
  it('keeps classification and request context without SQL, params, stack or cause messages', () => {
    const lines: string[] = []
    const log = createLogger(
      { level: 'info', pretty: false },
      {
        write(line) {
          lines.push(line)
        },
      },
    )
    const cause = Object.assign(new Error('secret-cause-email@example.test'), { code: '23505' })
    const error = new DrizzleQueryError(
      'insert secret-prompt',
      ['secret-lyrics', 'secret-base64'],
      cause,
    )
    log.child({ requestId: 'request-123' }).error({ err: error }, 'database operation failed')
    const output = lines.join('')
    for (const privateValue of ['secret-', 'insert ', 'params', 'stack'])
      expect(output).not.toContain(privateValue)
    expect(JSON.parse(output)).toMatchObject({
      requestId: 'request-123',
      err: { type: 'DrizzleQueryError', cause: { type: 'Error', code: '23505' } },
    })
  })
  it('ignores arbitrary classifications and safely handles cyclic causes', () => {
    const error = Object.assign(new Error('private'), {
      code: 'private-code',
      cause: undefined as unknown,
    })
    error.cause = error
    expect(serializeError(error)).toEqual({ type: 'Error', cause: { type: 'Error' } })
    expect(serializeError({ message: 'private', name: 'private' })).toEqual({ type: 'Error' })
  })
})
