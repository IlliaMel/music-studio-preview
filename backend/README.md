# Studio backend

The server behind the Studio Android app. It supports Firebase token verification and local development sign-in, keeps profiles and tracks, and runs the long music generations in the background, so the phone only creates a track and polls until it is ready. Generation runs on a mock provider for now; the ElevenLabs client, which will keep the API key away from the phone, comes next.

**Stack:** Node.js 24, TypeScript, [Hono](https://hono.dev), [zod](https://zod.dev), [Drizzle ORM](https://orm.drizzle.team) on Postgres, [PGlite](https://pglite.dev) for local work and tests, [pino](https://getpino.io) logs, [Vitest](https://vitest.dev), [Biome](https://biomejs.dev).

## Run it

```sh
cd backend
npm install
cp .env.example .env   # optional: every value has a local default
npm run dev            # http://127.0.0.1:8787, restarts on changes
```

`curl http://127.0.0.1:8787/health` answers `{"ok":true}`. Locally you sign in with a development token instead of Firebase:

```sh
curl -H "Authorization: Bearer dev:alice" http://127.0.0.1:8787/v1/me
```

`requests.http` has example sign-in and profile requests for the HTTP client in Android Studio.

Nothing else to install: without `DATABASE_URL` the server uses PGlite, a real Postgres compiled to WebAssembly that runs inside the Node process and keeps its files in `data/`. Delete that folder to start from an empty database.

**From the app:** the Android emulator reaches the computer at `http://10.0.2.2:8787`. A phone on USB reaches it at `http://localhost:8787` after `adb reverse tcp:8787 tcp:8787`.

## Commands

| Command | What it does |
|---|---|
| `npm run dev` | Server with reload on changes |
| `npm run check` | Lint, typecheck and tests: the same as CI |
| `npm test` / `npm run test:watch` | Tests once / on every change |
| `npm run format` | Fix formatting and import order |
| `npm run db:generate` | Write the SQL migration for a schema change into `drizzle/` |
| `npm run build` && `npm start` | Compile to `dist/` and run it, as in production |

## How the code is organised

```
src/
├─ main.ts           reads config, opens the database, creates the real services, starts the server
├─ compose.ts        creates every feature and wires the app; main.ts and the tests share it
├─ app.ts            createApp(deps): middleware, /health, feature routes under /v1, error handling
├─ core/             shared plumbing, no product rules
│  ├─ config.ts      environment → checked Config; the server refuses to start on a bad value
│  ├─ logger.ts      pino with secrets redacted
│  ├─ auth/          AuthProvider (Firebase or dev), the authenticate and requireOnboarding middleware
│  ├─ db/            database client (Postgres or PGlite), migrations, schema index
│  ├─ http/          request context, error format, zod validation, request logging, file responses
│  └─ storage/       AudioStorage: audio files on disk (a mounted volume when deployed)
└─ features/<name>/  one folder per feature
   ├─ <name>.table.ts       Drizzle table: the shape of the data
   ├─ <name>.repository.ts  SQL for that table, nothing else
   ├─ <name>.service.ts     the rules; knows nothing about HTTP
   ├─ <name>.routes.ts      thin HTTP layer: validate, call the service, map to JSON
   └─ index.ts              creates the feature from its dependencies
```

Dependencies point one way: routes → service → repository → database. `core` never imports from `features`, and a feature reaches another feature only through its service, never through its table.

**Errors.** Every failure has the same shape, `{"error": {"code", "message", "details?"}}`. Throw `new ApiError(status, 'CODE', 'message')` from any layer; anything else becomes `500 INTERNAL` and is logged with its stack, without leaking details to the client. Each response carries `X-Request-Id`, also present on every log line of that request.

## Sign-in

The Firebase backend contract expects a Firebase ID token sent with every request as `Authorization: Bearer <token>`. The current Android prototype uses demo/development sign-in; real provider UI and credentials are not validated. The server never sees passwords and keeps no sessions:

1. `authenticate` checks the token's signature, expiry and audience with the Firebase Admin SDK, using Google's cached public keys, and asks Firebase whether the account still exists and is enabled, so a deleted or blocked user is locked out at once.
2. `UsersService.resolve` finds our user by the Firebase uid, or creates them on their first request.
3. Routes read the user with `c.get('user')`. Routes that need a finished profile add `requireOnboarding`.

A token that is missing, malformed or expired gets `401 UNAUTHORIZED` with `WWW-Authenticate: Bearer`, and the app refreshes it or signs in again. Firebase being unreachable is a `500`, not a `401`, so the app does not sign the user out because of our outage.

| Endpoint | What it does |
|---|---|
| `GET /v1/me` | The profile and current terms acceptance status |
| `PATCH /v1/me` | Accepts current terms through `acceptedTermsVersion`; legacy optional profile/consent fields remain compatible |
| `DELETE /v1/me` | Deletes the account: our data first, then the Firebase user. Safe to retry |

When the terms change, bump `TERMS_VERSION` in `users.service.ts`: every user gets `terms` back in the missing steps and `requireOnboarding` stops them until they accept.

**Modes.** `AUTH_MODE=firebase` needs `FIREBASE_SERVICE_ACCOUNT`, the JSON key from Firebase console → Project settings → Service accounts, pasted as one line; the Admin SDK needs it to delete users. `AUTH_MODE=dev` accepts `dev:<name>` tokens, so tests, CI and anyone without our Firebase project can use the API. Because anyone could claim any name, the server refuses dev mode in production and on any address other than loopback.

## Tracks and generation

```
POST /v1/tracks  ──►  queued  ──► worker claims it ──►  running  ──►  done    (audio ready)
 Idempotency-Key                                            └──────►  failed  (code + suggestion)
```

The app creates a track and polls `GET /v1/tracks/:id` every few seconds until it is `done` or `failed`, then downloads `audioUrl` once. Generation takes from half a minute to a few minutes, so it never runs inside the request.

- **The queue is the `tracks` table.** Each worker loop claims the oldest `queued` row with `FOR UPDATE SKIP LOCKED`, so loops never take the same track and nothing queued lives only in memory. On shutdown the songs in progress are cancelled and put back in the queue for the next server; a track left `running` by a crash is failed as `INTERRUPTED` once it is clearly abandoned.
- **Idempotency.** The app sends an `Idempotency-Key` per attempt and reuses it on network retries; the same key returns the same track instead of paying for a second generation. The same key with a different body is `422 IDEMPOTENCY_KEY_REUSED`. "Try again" after a failure is a new attempt with a new key.
- **Daily limit** per user (`DAILY_GENERATIONS_PER_USER`, UTC days), checked under a per-user lock so parallel requests cannot slip past it. Failures the provider refuses up front (rejected prompt, rate limit, outage, no credits) do not count; a timeout or an interruption does, since the song may have been generated and paid for. Deleting a track wipes its content but does not give the generation back. `GET /v1/me/quota` shows what is left.
- **Retries and timeout.** Rate limits and a briefly unavailable provider are retried after 2, 4 and 8 seconds; a rejected prompt is not. Each song gives up after `GENERATION_TIMEOUT_MS`.
- **Audio** is served with `Range` support, so the player can seek without downloading the whole file again. It is kept in `AUDIO_DIR` on the server's disk, so run **one** instance with a persistent volume; several instances need an object store behind `AudioStorage`.

| Endpoint | What it does |
|---|---|
| `POST /v1/tracks` | Queue a generation: `prompt`, `lengthMs` (omit for Auto), `instrumental`, `modelId`. Needs accepted current terms; age/name onboarding is not required |
| `GET /v1/tracks?limit&cursor` | The user's tracks, newest first, a page at a time |
| `GET /v1/tracks/:id` | One track, with the timing of every sung word |
| `GET /v1/tracks/:id/audio` | The audio file (WAV for mock, MP3 for a future provider); `409 NOT_READY` until it is done |
| `DELETE /v1/tracks/:id` | Delete a track and its audio, in any state |
| `GET /v1/me/quota` | Generations used and left today |

**Mock provider.** Answers after `MOCK_DELAY_MS` with an audible WAV of the requested length, a title from the prompt and evenly spaced lyrics. A marker in the prompt plays out a failure, to try every error screen of the app: `[mock:bad_prompt]`, `[mock:credits]`, `[mock:rate_limit]`, `[mock:server]`, `[mock:slow]`.

## Adding a feature

For a new feature (library/playlists already exists):

1. **Table.** `src/features/playlists/playlists.table.ts` with `pgTable(...)`, then export it from `src/core/db/schema.ts`.
2. **Migration.** `npm run db:generate -- --name create_playlists` writes the SQL to `drizzle/`. Read it and commit it; the server applies it on the next start. CI fails if a schema change has no migration.
3. **Repository.** Typed queries in `playlists.repository.ts`; a wrong column name is a compile error.
4. **Service.** The rules in `playlists.service.ts`, with dependencies passed to the constructor.
5. **Routes.** `playlists.routes.ts` returns a `Hono<AppEnv>`; validate input with `validate('json', schema)` and read it with `c.req.valid('json')`.
6. **Register.** Create the feature in `compose.ts` and add it to the `features` list; the tests pick it up from there too.
7. **Tests.** `createTestDatabase()` gives a migrated in-memory Postgres; `truncateAll()` empties it between tests. Route tests call `app.request(...)`, without a network.

## Database

- **Locally and in tests:** PGlite, nothing to install and no Docker.
- **Deployed:** any Postgres through `DATABASE_URL` (for example Railway's). In production the server also requires `PUBLIC_BASE_URL` (the HTTPS address the app uses, for audio links) and `AUDIO_DIR` (a persistent volume), and refuses to start without them.
- **Migrations** are plain SQL files generated from the schema and reviewed in pull requests. The server applies the pending ones at start, so a deploy is a single step.

## API contract

After Firebase/Google sign-in, GET /v1/me returns a provider/default display name. Accept the displayed terms with PATCH /v1/me {"acceptedTermsVersion":"2026-10-14"}. Age confirmation and name onboarding are not required. Existing age-consent fields remain backward compatible.

POST /v1/tracks requires Idempotency-Key (8–100 characters) and accepts:

```json
{"prompt":"A bright electronic melody","instrumental":false,"variants":2,"quality":"auto","lengthMs":30000,"modelId":"music_v2_5","customLyrics":"Hello Kyiv tonight"}
```

Quality accepts auto/deep/fast and is persisted/passed to the provider; the mock does not change rendering quality. Optional reference fields: referenceUploadId (owned upload UUID), remixTrackId (catalog ID or owned ready track UUID). Remix-of-remix is rejected. The response preserves the first track DTO and adds generationId, variantIndex, variants, and tracks containing all variants. Two variants reserve ONE daily generation atomically; the default limit is 25. Repeating the same key/body returns the same group; changing the body returns 422. Retry failed generations with a new key.

POST /v1/uploads accepts raw audio/mpeg, audio/mp4, audio/wav, audio/x-wav, audio/aac, audio/ogg or audio/webm up to 20 MiB and returns {id,contentType,sizeBytes,durationMs:null,provider:"mock"}. Upload ownership is checked on generation. Reference bytes are persisted in PGlite/Postgres; object storage and audio inspection belong with the real provider adapter.

| Method | Path | Response |
|---|---|---|
| GET | /v1/catalog | {collections,stations,tracks} |
| GET | /v1/collections/:id | Collection with tracks |
| GET | /v1/stations/:id | Station with tracks |
| GET | /v1/likes | {trackIds} |
| PUT / DELETE | /v1/likes/:trackId | 204 |
| GET | /v1/playlists | {playlists} |
| POST | /v1/playlists | Playlist; body {name} |
| GET / PATCH / DELETE | /v1/playlists/:id | Playlist / 204; PATCH body {name} |
| PUT / DELETE | /v1/playlists/:id/tracks/:trackId | 204 |

Playlist DTO: {id,name,createdAt,trackIds}. Track IDs match the 12-track Android built-in demo catalog. Likes, playlists and uploads persist in the database and are deleted with the account. Deleting a generated track also removes its library references.

The mock generates audible PCM WAV of exactly the requested duration (Auto:30 seconds), maps supplied words to synthetic lyric timings, and varies melodies between variants. Audio endpoints return Content-Type:audio/wav and support byte ranges. These are deterministic demo tones, not ElevenLabs music or synthesized singing. Remix/upload contracts are stored; the mock does not interpret or transform reference audio. Real ElevenLabs and live Firebase credentials remain unvalidated.

PATCH /v1/tracks/:id accepts {title:string} (trimmed, 1–80 characters) and returns the persisted track DTO. Only owned done/failed tracks can be renamed; queued/running returns 409 NOT_READY, foreign/deleted returns 404. This prevents a finishing provider from overwriting an in-flight rename.

Idempotency-Key values reserve the substring `:variant:` for internal variant rows; client keys containing it are rejected with 400. Deleted queued tracks are never sent to the provider. Library membership additions lock the referenced owned track in the same transaction as insertion, so concurrent track deletion cannot leave orphan references.

Account deletion uses a durable deletionRequestedAt marker (migration0003). Other requests receive409 ACCOUNT_DELETING while DELETE /v1/me remains retryable. Tombstoned tracks cannot save new audio; workers hold a track row lock through audio save and completion, so cleanup either waits for that save or prevents it. Single-track DELETE also marks first and can retry failed file cleanup after the track is hidden. Catalog audio links honor PUBLIC_BASE_URL.
