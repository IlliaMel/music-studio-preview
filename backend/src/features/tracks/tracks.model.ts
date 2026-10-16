export type Word = { word: string; startMs: number; endMs: number }

export const remixSourceIds = (value: {
  remixTrackId?: string | null
  remixTrackIds?: string[] | null
}): string[] =>
  value.remixTrackIds?.length ? value.remixTrackIds : value.remixTrackId ? [value.remixTrackId] : []
