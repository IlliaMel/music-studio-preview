export const isMp3 = (data: Buffer): boolean =>
  data.length >= 4 &&
  (data.toString('ascii', 0, 3) === 'ID3' || (data[0] === 255 && ((data[1] ?? 0) & 224) === 224))

export function validAudio(data: Buffer, type: string): boolean {
  if (data.length < 12 || data.length > 20 * 1024 * 1024) return false
  if (type === 'audio/mpeg') return isMp3(data)
  if (type === 'audio/mp4')
    return data.toString('ascii', 4, 8) === 'ftyp' && data.includes(Buffer.from('mdat'))
  return (
    (type === 'audio/wav' || type === 'audio/x-wav') &&
    data.toString('ascii', 0, 4) === 'RIFF' &&
    data.toString('ascii', 8, 12) === 'WAVE' &&
    data.includes(Buffer.from('data'))
  )
}
