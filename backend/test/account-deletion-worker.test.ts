import { expect, it, vi } from 'vitest'
import { AccountDeletionWorker } from '../src/features/users/account-deletion-worker.js'
import { silentLogger } from './support/logger.js'

it('scans at startup, retries periodically, and stops scheduling after shutdown', async () => {
  vi.useFakeTimers()
  const recoverDeletion = vi.fn().mockResolvedValue(true)
  const worker = new AccountDeletionWorker(
    { pendingDeletions: async () => [{ id: 'pending' }], recoverDeletion },
    silentLogger,
    10,
  )
  try {
    worker.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(recoverDeletion).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(10)
    expect(recoverDeletion).toHaveBeenCalledTimes(2)
    await worker.stop()
    await vi.advanceTimersByTimeAsync(100)
    expect(recoverDeletion).toHaveBeenCalledTimes(2)
  } finally {
    await worker.stop()
    vi.useRealTimers()
  }
})

it('coalesces concurrent scans and drains active cleanup on shutdown', async () => {
  let release: () => void = () => {}
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  const worker = new AccountDeletionWorker(
    {
      pendingDeletions: async () => [{ id: 'pending' }],
      recoverDeletion: async () => {
        await gate
        return true
      },
    },
    silentLogger,
  )
  const first = worker.runOnce()
  expect(worker.runOnce()).toBe(first)
  let stopped = false
  const stop = worker.stop().then(() => {
    stopped = true
  })
  await Promise.resolve()
  expect(stopped).toBe(false)
  release()
  expect(await first).toBe(1)
  await stop
  expect(stopped).toBe(true)
})
