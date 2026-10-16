# Ordered remix sources

POST /v1/tracks accepts remixTrackIds, an ordered array of up to four distinct original IDs in mock mode. New clients should send the array only when nonempty. The legacy remixTrackId scalar remains accepted when the array field is absent, and normalizes to a one-element array. Supplying both fields, even an empty array, returns 400 VALIDATION. Duplicate IDs and arrays over four return 400; an uploaded reference together with remix originals returns 422 INVALID_REFERENCE. An empty array is ordinary prompt generation.

Responses return ordered remixTrackIds plus legacy remixTrackId as the first source or null. Migration 0007 backfills old scalar sources into the new array; reads and idempotency comparisons also fall back to the scalar for old rows. Array order is part of request identity. A legacy scalar request and its matching singleton array reuse the same Idempotency-Key and return the existing group without spending another credit. Reordering or changing sources with the same key returns 422 IDEMPOTENCY_KEY_REUSED. All variants persist the same source identity and the worker forwards every source after restart.

Each generated source must belong to the current owner, be visible, Ready and an Original. Every source is checked, not only the first. A missing/deleted/foreign source returns 404 TRACK_NOT_FOUND, a remix returns 409 REMIX_OF_REMIX, and unfinished originals return 409 NOT_READY. SQL repeats these checks under sorted source row locks before inserting the group or reserving quota, preventing deletion races. Public fictional catalog originals are allowed only in mock mode. Recording Drafts remain separate: use their bytes through the uploaded-reference contract, not recording IDs as remix originals.

GET /v1/capabilities reports maxRemixSources=4 for mock, 1 for the current entitled live adapter and 0 when live references are disabled. remixOutput is fixture-no-audio-blending for mock and single-source-conditioning for live. Mock output is synthesized fixture audio: accepting multiple source IDs does not claim to blend or transform their audio. One group still uses one local daily generation credit even with two variants.

Live requests with more than one original are rejected as 422 UNSUPPORTED_MODE before queue insertion, quota reservation, reference access or paid HTTP. The adapter itself also fails closed. Single-source live remix still requires the owner's completed ElevenLabs original with a reusable stored song ID and configured inpainting entitlement. Catalog mock sources are not silently uploaded or billed to enable live remix. Uncertain paid-request no-retry behavior remains unchanged.

The official v2/v2.5 schema permits one conditioning_ref per generation chunk and a composition plan containing multiple chunks. Distinct per-chunk song IDs are a plausible future ordered-section mapping, not a verified simultaneous blending contract. The current live adapter uses one source; maxRemixSources must not be increased until it consumes every source and synthetic/live integration verification is complete. Official sources checked 2026-10-16:

- https://elevenlabs.io/docs/eleven-api/guides/how-to/music/inpainting
- https://raw.githubusercontent.com/elevenlabs/elevenlabs-python/main/src/elevenlabs/types/generation_chunk_input.py
- https://raw.githubusercontent.com/elevenlabs/elevenlabs-python/main/src/elevenlabs/types/audio_ref_chunk.py

AllowRemixes and LockLyrics policies, publishing and actual multi-source live generation are not implemented by this contract and remain explicitly outside this change. No provider billing, key activation or deployment was performed.
