export type Word = { word: string; startMs: number; endMs: number }

/**
 * The words that are actually sung. ElevenLabs also returns a composition plan's cues and section
 * labels, such as `{generate vocals}` or `[Chorus]`, as words; lyrics must not show them.
 */
export function sungWords(words: Word[]): Word[] {
  let closing: '}' | ']' | undefined
  return words.filter(({ word }) => {
    const text = word.trim()
    if (!closing && text.startsWith('{')) closing = '}'
    else if (!closing && text.startsWith('[')) closing = ']'
    if (!closing) return true
    if (text.endsWith(closing)) closing = undefined
    return false
  })
}

export const remixSourceIds = (value: {
  remixTrackId?: string | null
  remixTrackIds?: string[] | null
}): string[] =>
  value.remixTrackIds?.length ? value.remixTrackIds : value.remixTrackId ? [value.remixTrackId] : []
