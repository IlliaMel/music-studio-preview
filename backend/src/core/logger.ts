import { type Logger, pino } from 'pino'
import type { Config } from './config.js'

export type { Logger }

export function createLogger(config: Config['log']): Logger {
  return pino({
    level: config.level,
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
  })
}
