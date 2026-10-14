import type { ByteRange, StoredFile } from '../storage/audio-storage.js'

// Node's web streams and the Response body type are the same thing under two type names.
type ResponseBody = ConstructorParameters<typeof Response>[0]

/**
 * Answers with a stored file and honours `Range`, which is how ExoPlayer seeks: it asks for
 * the bytes from the new position instead of downloading the whole song again.
 */
export function fileResponse(
  file: StoredFile,
  contentType: string,
  rangeHeader: string | undefined,
): Response {
  const headers = new Headers({ 'Content-Type': contentType, 'Accept-Ranges': 'bytes' })
  const range = parseRange(rangeHeader, file.size)

  if (range === 'unsatisfiable') {
    headers.set('Content-Range', `bytes */${file.size}`)
    return new Response(null, { status: 416, headers })
  }
  if (!range) {
    headers.set('Content-Length', String(file.size))
    return new Response(file.stream() as ResponseBody, { status: 200, headers })
  }
  headers.set('Content-Length', String(range.end - range.start + 1))
  headers.set('Content-Range', `bytes ${range.start}-${range.end}/${file.size}`)
  return new Response(file.stream(range) as ResponseBody, { status: 206, headers })
}

/**
 * One range in any of its three forms: `bytes=0-499`, `bytes=500-`, `bytes=-500` (the last 500).
 * Anything else, such as several ranges at once, gets the whole file, which HTTP allows.
 */
export function parseRange(
  header: string | undefined,
  size: number,
): ByteRange | 'unsatisfiable' | undefined {
  const match = header?.trim().match(/^bytes=(\d*)-(\d*)$/)
  if (!match) return undefined
  const [, from = '', to = ''] = match
  if (from === '' && to === '') return undefined

  if (from === '') {
    const suffix = Number(to)
    if (suffix === 0 || size === 0) return 'unsatisfiable'
    return { start: Math.max(0, size - suffix), end: size - 1 }
  }
  const start = Number(from)
  const end = to === '' ? size - 1 : Math.min(Number(to), size - 1)
  if (start >= size || end < start) return 'unsatisfiable'
  return { start, end }
}
