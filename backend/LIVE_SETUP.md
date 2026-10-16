# Live backend activation

The same backend supports mock and real ElevenLabs music. No paid requests were made during implementation or tests. Mock remains the default. Android supports explicit endpoint/auth-mode configuration; actual Google/Firebase sign-in remains separate integration work. Backend credentials alone do not implement Android identity flows.

## Local server with real music

Copy .env.example to .env, set MUSIC_PROVIDER=elevenlabs and ELEVENLABS_API_KEY to your server-side ElevenLabs key. Keep AUTH_MODE=dev and HOST=127.0.0.1 for local emulator testing. Run npm install, npm run build, npm start. Migrations apply automatically at startup. A valid key with Music API access activates prompt/instrumental generation, custom lyrics, MP3 playback/export, metadata and provider word timestamps. Set MUSIC_PROVIDER=mock for the fast development fixture flow; MOCK_DELAY_MS controls the delay.

AUTH_MODE=firebase activates Firebase Admin token verification/revocation checking and account deletion. Supply either FIREBASE_SERVICE_ACCOUNT as JSON or FIREBASE_SERVICE_ACCOUNT_FILE as an absolute JSON-file path; inline JSON wins if both are present. This is the backend admin credential, not Android google-services.json. The HTTP client must supply a real Firebase ID token. Never commit credentials or put them in the phone.

Production requires an explicit MUSIC_PROVIDER=elevenlabs (and its key), or MUSIC_PROVIDER=mock only when fixtures are intentionally desired in a production-like staging environment. Omitting MUSIC_PROVIDER fails startup in production; local development still defaults to mock. Production additionally requires NODE_ENV=production, DATABASE_URL for Postgres, PUBLIC_BASE_URL=https://your-api-host, AUDIO_DIR on a persistent volume and Firebase credentials. Terminate HTTPS at your ingress; use an appropriately protected HOST. The local-volume deployment supports one server instance. Multiple instances need a shared audio/object store. API keys are sent only to the fixed official ElevenLabs origin; redirects are refused.

## Supported real modes and costs

- Auto quality is supported. Deep/Fast have no documented ElevenLabs generation-quality parameter and return UNSUPPORTED_MODE before queue insertion. They remain available as mock UI options.
- Prompt generations use /v1/music/detailed with model music_v2 or music_v2_5, requested duration and force_instrumental. The detailed multipart response supplies JSON metadata and MP3 bytes. Word timestamps are provider values; absent timings stay empty.
- Custom lyrics use v2 composition-plan chunks, preserving lines. Each line must contain at most 200 characters, each chunk at most 30 lines and 3–120 seconds. Long lyrics/durations split into chunks; lyrics plus instrumental is rejected. Auto length with lyrics/reference selects 120 seconds. Plain prompt Auto length lets the provider choose. Two variants are two independently generated provider songs; the app reserves one daily generation credit for the group, while ElevenLabs can charge for both requests.
- References/remix require enterprise inpainting entitlement. Enable ELEVENLABS_ENABLE_REFERENCES=true only on an entitled account. Uploaded audio is transferred using /v1/music/upload, its extracted v2 plan validates duration, and generation uses a conditioning_ref with the first up-to-30-second slice. This conditions new music; it does not clone a voice or guarantee an exact reproduction. Voice cloning, stems and video-to-music are outside this API contract and are not silently simulated in real mode.
- Upload to ElevenLabs is itself billable, including possible partial charges on copyright rejection. Local POST /v1/uploads only stores bytes and does not contact ElevenLabs; transfer happens when a referenced generation runs. MP3, WAV and MP4/M4A container signatures and byte bounds are checked locally; ElevenLabs performs full codec/copyright inspection and extracted-plan duration validation (3 seconds–10 minutes). Signature checks are not a full local audio decoder. Other local mock MIME formats are explicitly unsupported in real mode.
- Completed live originals stored with references enabled can be remixed by owner. Mock catalog/original tracks cannot be remixed with the live provider and return UNSUPPORTED_MODE. Generated originals without a reusable provider song ID cannot be remixed. Remix-of-remix remains forbidden.

GET /v1/capabilities reports the selected provider, supported qualities, reference enablement, maxRemixSources and upload limits. Ordered multi-source mock remix is supported; the current live adapter has a hard one-source bound and rejects larger arrays before quota or paid work. See REMIX.md. Never infer entitlement merely from a successful local upload.

## Paid request interruption and recovery

The database running row is recorded before the provider POST. Its track UUID is a durable local attempt identity and a stable seed for composition-plan variants; it is not a provider idempotency key. Official compose/upload contracts expose no idempotency-key or accepted-job reconciliation endpoint, so exactly-once remote billing is not promised.

Live paid requests are never automatically retried, including rate limits, disconnects, shutdown and 5xx responses. A lost/incomplete response becomes terminal PROVIDER_OUTCOME_UNKNOWN, with an instruction to inspect ElevenLabs request history before intentionally submitting a new request. Reusing the app Idempotency-Key returns the same failed group and never starts another provider request. Crashed running live rows become unknown after the configured timeout plus one minute. Queued work is provider-labelled; switching modes cannot accidentally send old mock jobs to a paid provider.

Reference transfers record provider_attempted before POST and persist provider_song_id/duration after success. Parallel variants share one transfer in this process and later requests reuse the stored ID. If a transfer result is uncertain, its marker blocks automatic re-upload after restart. Inspect provider history before deleting/re-uploading that reference intentionally. No supported remote song deletion endpoint is documented in the cited Music contract: deleting local uploads/tracks/accounts removes our DB/files, but provider-side retention/deletion must follow the ElevenLabs account agreement/support process. No fabricated remote-delete call is issued.

The global logger serializes only safe error classification/codes, never raw SQL/provider messages, parameters, bodies or nested cause text. Request IDs remain available. Account deletion has startup and periodic recovery independent of a revoked principal token.

## Upload retention and playback

Each account may hold 20 uploads, 100 MiB combined, and 20 MiB per file. Local uploads expire after 24 hours. Expired unused uploads are removed at startup, hourly and before a new upload; active queued/running references stay until completion, then the next sweep removes them. DELETE /v1/uploads/:id is ownership-safe and refuses active references. Account deletion cascades all uploads. Storage uses bounded SQL base64 payloads for this single-instance MVP; replace with object storage if volume grows.

Catalog fixtures stream synthesized WAV in 64 KiB chunks. HEAD computes metadata only; Range synthesizes only requested samples. Generated audio is served from persistent storage with MIME sniffing and byte ranges.

## Verification and remaining external checks

Tests use synthetic HTTP multipart/audio fixtures and fake Firebase APIs, plus PGlite and the pg wire driver. Run npm run check and npm run build. No real credentials, paid calls, live account deletion, actual enterprise entitlement or independent Postgres server were exercised. Validate those with your own staging credentials before production. A backend credential cannot implement missing Android Google/Firebase sign-in; configure the client endpoint/auth mode explicitly.

Official contracts checked 2026-10-15:

- [Detailed music generation](https://elevenlabs.io/docs/api-reference/music/compose-detailed)
- [Music upload and its billing notice](https://elevenlabs.io/docs/api-reference/music/upload)
- [Official Music SDK skill: v2 chunks, enterprise inpainting and conditioning](https://github.com/elevenlabs/skills/blob/main/music/references/api_reference.md)
- [Official generated SDK response/word-timestamp schemas](https://github.com/elevenlabs/elevenlabs-python/tree/main/src/elevenlabs/types)

## HTTP admission limits

Each server process admits at most 1200 API requests/minute before authentication, 240 requests/minute per verified owner, 4 concurrent binary uploads overall and 2 per owner. Configure positive HTTP_REQUESTS_PER_MINUTE, HTTP_OWNER_REQUESTS_PER_MINUTE, HTTP_CONCURRENT_UPLOADS and HTTP_OWNER_CONCURRENT_UPLOADS (upload concurrency max 100). Fixed windows return 429 RATE_LIMITED with Retry-After; saturation rejects uploads before reading/materializing bytes. Slots release on handler success/failure. Owner bookkeeping is capped at 10000 active windows. Requests authenticate before body validation; unauthenticated oversized bodies receive 401. Health probes remain outside API admission.

These counters are bounded in-memory per process, reset on restart, and are not distributed billing/security quotas. Forwarded IP headers are deliberately ignored; no proxy trust is invented. Configure ingress connection/body/time limits and, if needed, distributed/per-IP limits for public deployment, especially before slow unauthenticated connections and across replicas. Authentication itself is bounded by the pre-auth global request rate; upload slots apply after authentication. No network/paid integration was exercised by these local regressions.
