import { type DestinationStream, type Logger, pino } from 'pino'
import type { Config } from './config.js'
import { serializeError } from './safe-error.js'

export type { Logger }

export function createLogger(config: Config['log'], destination?: DestinationStream): Logger {
  return pino(
    {
      level: config.level,
      serializers: { err: serializeError, error: serializeError },
      // Tokens and keys must never reach the logs, even by accident.
      redact: {
        paths: ['authorization', '*.authorization', 'token', '*.token', 'apiKey', '*.apiKey'],
        censor: '[redacted]',
      },
      ...(config.pretty && {
        transport: {
          target: 'pino-pretty',
          options: { translateTime: 'HH:MM:ss', ignore: 'pid,hostname' },
        },
      }),
    },
    destination,
  )
}
