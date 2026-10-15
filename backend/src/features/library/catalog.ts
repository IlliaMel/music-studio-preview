import fixture from './catalog.fixture.json' with { type: 'json' }

export const catalogMetadata = {
  schemaVersion: fixture.schemaVersion,
  catalogVersion: fixture.catalogVersion,
  provenance: fixture.provenance,
  trendSnapshots: fixture.trendSnapshots,
}
export const catalogTracks = fixture.tracks
export const collections = fixture.collections
export const stations = fixture.stations
