import { randomUUID } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { mkdir, open, rename, rm, stat, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { Readable } from 'node:stream'
import type { ReadableStream } from 'node:stream/web'

export type ByteRange = { start: number; end: number }

export type StoredFile = {
  size: number
  contentType?: string
  /** The whole file, or the inclusive byte range a player asked for when seeking. */
  stream(range?: ByteRange): ReadableStream<Uint8Array>
}

/**
 * Where the generated MP3s live. Files on a local disk for now (a mounted volume when deployed);
 * an object store such as Cloudflare R2 can implement the same three calls later.
 */
export interface AudioStorage {
  save(key: string, audio: Uint8Array): Promise<void>
  open(key: string): Promise<StoredFile | undefined>
  /** Does nothing when the file is already gone. */
  delete(key: string): Promise<void>
}

const SAFE_KEY = /^[A-Za-z0-9-]+$/

export class LocalAudioStorage implements AudioStorage {
  constructor(private readonly directory: string) {}

  async save(key: string, audio: Uint8Array): Promise<void> {
    await mkdir(this.directory, { recursive: true })
    // Written aside and renamed, so a reader never sees half a file.
    const temporary = join(this.directory, `.${key}.${randomUUID()}.tmp`)
    await writeFile(temporary, audio)
    await rename(temporary, this.path(key))
  }

  async open(key: string): Promise<StoredFile | undefined> {
    let path = this.path(key)
    try {
      try {
        await stat(path)
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
        path = join(this.directory, `${key}.mp3`)
      }
      const { size } = await stat(path)
      const handle = await open(path, 'r')
      const header = Buffer.alloc(12)
      try {
        await handle.read(header, 0, 12, 0)
      } finally {
        await handle.close()
      }
      const contentType =
        header.toString('ascii', 0, 4) === 'RIFF' && header.toString('ascii', 8, 12) === 'WAVE'
          ? 'audio/wav'
          : 'audio/mpeg'
      return {
        size,
        contentType,
        stream: (range) =>
          Readable.toWeb(createReadStream(path, range)) as ReadableStream<Uint8Array>,
      }
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined
      throw error
    }
  }

  async delete(key: string): Promise<void> {
    const path = this.path(key)
    await Promise.all([
      rm(path, { force: true }),
      rm(join(this.directory, `${key}.mp3`), { force: true }),
    ])
  }

  private path(key: string): string {
    // Keys are our own track ids; refusing anything else rules out paths like ../../etc.
    if (!SAFE_KEY.test(key)) throw new Error(`Unsafe storage key: ${key}`)
    return join(this.directory, `${key}.audio`)
  }
}
