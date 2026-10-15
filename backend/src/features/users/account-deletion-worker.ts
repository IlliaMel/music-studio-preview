import type { Logger } from '../../core/logger.js'
import type { UsersService } from './users.service.js'

export class AccountDeletionWorker {
  private running = false
  private timer?: NodeJS.Timeout
  private active?: Promise<number>
  constructor(
    private readonly service: Pick<UsersService, 'pendingDeletions' | 'recoverDeletion'>,
    private readonly log: Logger,
    private readonly intervalMs = 30_000,
  ) {}
  start(): void {
    if (this.running) return
    this.running = true
    this.tick()
  }
  async stop(): Promise<void> {
    this.running = false
    clearTimeout(this.timer)
    await this.active?.catch(() => {})
  }
  runOnce(): Promise<number> {
    if (this.active) return this.active
    const work = this.recover()
    this.active = work
    void work
      .finally(() => {
        if (this.active === work) this.active = undefined
      })
      .catch(() => {})
    return work
  }
  private tick(): void {
    void this.runOnce()
      .catch((error) => this.log.error({ err: error }, 'account cleanup scan failed'))
      .finally(() => {
        if (this.running) this.timer = setTimeout(() => this.tick(), this.intervalMs).unref()
      })
  }
  private async recover(): Promise<number> {
    const pending = await this.service.pendingDeletions()
    let recovered = 0
    for (const user of pending) {
      try {
        if (await this.service.recoverDeletion(user.id)) {
          recovered++
          this.log.info({ userId: user.id }, 'pending account deletion completed')
        }
      } catch (error) {
        this.log.warn({ userId: user.id, err: error }, 'account deletion remains pending')
      }
    }
    return recovered
  }
}
