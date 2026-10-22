import { mkdir, mkdtemp, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { expect, it } from 'vitest'
import { LocalAudioStorage } from '../src/core/storage/audio-storage.js'

it('checks at startup that it can write, and leaves nothing behind', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'storage-writable-'))
  try {
    const audio = join(directory, 'audio')
    await new LocalAudioStorage(audio).ensureWritable()
    expect(await readdir(audio)).toEqual([])

    await writeFile(join(directory, 'not-a-folder'), 'bytes')
    await expect(
      new LocalAudioStorage(join(directory, 'not-a-folder', 'audio')).ensureWritable(),
    ).rejects.toThrow()
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

it('removes the temporary file when the final rename fails', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'storage-failure-'))
  try {
    await mkdir(join(directory, 'track.audio'))
    await expect(
      new LocalAudioStorage(directory).save('track', new Uint8Array([1, 2])),
    ).rejects.toThrow()
    expect(await readdir(directory)).toEqual(['track.audio'])
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

it('removes only the deleted key’s final files and owned UUID temporary files', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'storage-owned-'))
  const uuid = '12345678-1234-1234-1234-123456789abc'
  const names = [
    'track.audio',
    'track.mp3',
    `.track.${uuid}.tmp`,
    `.other.${uuid}.tmp`,
    '.track.unknown.tmp',
  ]
  try {
    await Promise.all(names.map((name) => writeFile(join(directory, name), 'bytes')))
    await new LocalAudioStorage(directory).delete('track')
    expect((await readdir(directory)).sort()).toEqual(
      [`.other.${uuid}.tmp`, '.track.unknown.tmp'].sort(),
    )
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})
