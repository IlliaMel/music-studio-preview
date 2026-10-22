# Studio backend

The server behind the Studio Android app. It supports Firebase token verification and local development sign-in, keeps profiles and tracks, and runs music generations in the background while the phone polls for completion. Generation supports a fast local mock and the real ElevenLabs Music API. Choose the provider in the environment; API keys remain on the server. See [LIVE_SETUP.md](LIVE_SETUP.md) for activation, supported modes and paid-attempt safety.

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

For a physical phone over Wi-Fi, connect both devices to the same trusted local network. Set the backend environment to the computer's actual private IPv4 address, for example:

```dotenv
NODE_ENV=development
HOST=<your-private-ip>
PORT=8789
AUTH_MODE=dev
ALLOW_LAN_DEV_AUTH=true
MUSIC_PROVIDER=mock
DATA_DIR=./data-phone-test
PUBLIC_BASE_URL=http://<your-private-ip>:8789
```

Replace `<your-private-ip>` with the computer's literal private IPv4 address. Use a separate test data directory and launch only one backend per directory. Configure the Android test build with the matching `http://<your-private-ip>:8789` base URL; replacing the address requires rebuilding that APK. Check `/health` from the phone's browser first. The computer's firewall must allow that backend port from the private network; Wi-Fi client isolation can prevent communication even on the same Wi-Fi.

LAN development sign-in lets anyone who can reach the port impersonate any `dev:<name>` account. Use test data on a trusted LAN and stop the server after testing. The opt-in only permits literal RFC1918 IPv4 addresses (`10/8`, `172.16/12`, `192.168/16`) in development; public hosts, hostnames, wildcard binds and production/test environments are refused. Keep `MUSIC_PROVIDER=mock` for this test setup: it makes no paid provider calls. Default loopback-only development behavior remains unchanged.

## Commands

| Command | What it does |
|---|---|
| `npm run dev` | Server with reload on changes |
| `npm run check` | Lint, typecheck and tests: the same as CI |
| `npm test` / `npm run test:watch` | Tests once / on every change |
| `npm run format` | Fix formatting and import order |
| `npm run db:generate` | Write the SQL migration for a schema change into `drizzle/` |
| `npm run build` && `npm start` | Compile to `dist/` and run it, as in production |

Tests run on in-memory PGlite, which executes one transaction at a time. The lock-race suite needs a real server and runs only when `TEST_DATABASE_URL` points at an empty Postgres database; CI provides one.

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

Dependencies point one way: routes → service → repository → database. `core` never imports from `features`, cross-feature SQL operations coordinate account, track and library ownership/deletion in shared transactions. These deliberate table references preserve atomic cleanup.

**Errors.** Every failure has the same shape, `{"error": {"code", "message", "details?"}}`. Throw `new ApiError(status, 'CODE', 'message')` from any layer; anything else becomes `500 INTERNAL` and is logged with safe classification/code and sanitized causes, without SQL parameters, messages or stacks. Each response carries `X-Request-Id`, also present on every log line of that request.

## Sign-in

The Firebase backend contract expects a Firebase ID token sent with every request as `Authorization: Bearer <token>`. The server never sees passwords and keeps no sessions:

1. `authenticate` checks the token's signature, expiry and audience with the Firebase Admin SDK, using Google's cached public keys, and asks Firebase whether the account still exists and is enabled, so a deleted or blocked user is locked out at once.
2. `UsersService.resolve` finds our user by the Firebase uid, or creates them on their first request.
3. Routes read the user with `c.get('user')`. Routes that need a finished profile add `requireOnboarding`.

A token that is missing, malformed or expired gets `401 UNAUTHORIZED` with `WWW-Authenticate: Bearer`, and the app refreshes it or signs in again. Firebase being unreachable is a `500`, not a `401`, so the app does not sign the user out because of our outage.

| Endpoint | What it does |
|---|---|
| `GET /v1/me` | The profile, current terms acceptance status and `canGenerate` |
| `PATCH /v1/me` | Accepts current terms through `acceptedTermsVersion`; legacy optional profile/consent fields remain compatible |
| `DELETE /v1/me` | Marks deletion durably; cleans audio/tracks, Firebase, then SQL account data; startup/periodic recovery can finish without the deleted token |

When the terms change, bump `TERMS_VERSION` in `users.service.ts`: every user gets `terms` back in the missing steps and `requireOnboarding` stops them until they accept.

**Shared Firebase project.** `FIREBASE_DELETE_USERS` defaults to `true`: account deletion removes the Firebase identity after local cleanup, including recovery after a restart. Set it to `false` in a development environment sharing production's Firebase project. Deletion then removes this environment's SQL data and local audio, while preserving the shared Firebase identity. It does not erase data from another environment's database.

**Generation access.** `GENERATION_ALLOWED_EMAILS` is an optional comma-separated allowlist, for example `someone@example.com`. Matching trims entries and ignores case, and requires the authenticated email to be verified. With a configured list, `POST /v1/tracks` and `POST /v1/uploads` return `403 GENERATION_NOT_ALLOWED` outside it. Profiles expose `canGenerate` so clients can present invitation-only generation. Authentication, browsing, private library access and account deletion remain available. Without a list, local and existing integrations retain their previous behavior. `MUSIC_PROVIDER=elevenlabs` with `AUTH_MODE=firebase` (always the case in production) requires a non-empty list at startup, whatever `NODE_ENV` says.

**Modes.** `AUTH_MODE=firebase` needs `FIREBASE_SERVICE_ACCOUNT` (inline JSON) or `FIREBASE_SERVICE_ACCOUNT_FILE` (credential path), the JSON key from Firebase console → Project settings → Service accounts, pasted as one line; the Admin SDK needs it to delete users. `AUTH_MODE=dev` accepts `dev:<name>` tokens, so tests, CI and anyone without our Firebase project can use the API. Because anyone could claim any name, dev mode defaults to loopback and is always refused in production. Physical-phone LAN testing requires the explicit private-address development opt-in described above.

## Tracks and generation

```
POST /v1/tracks  ──►  queued  ──► worker claims it ──►  running  ──►  done    (audio ready)
 Idempotency-Key                                            └──────►  failed  (code + suggestion)
```

The app creates a track and polls `GET /v1/tracks/:id` every few seconds until it is `done` or `failed`, then downloads `audioUrl` once. Generation takes from half a minute to a few minutes, so it never runs inside the request.

- **The queue is the `tracks` table.** Each worker loop claims the oldest `queued` row with `FOR UPDATE SKIP LOCKED`, so loops never take the same track and nothing queued lives only in memory. On shutdown mock songs are cancelled and requeued. Live paid songs are never blindly resubmitted: disconnects, shutdown and abandoned running rows become terminal `PROVIDER_OUTCOME_UNKNOWN`. Check provider history before intentionally starting another paid request.
- **Idempotency.** The app sends an `Idempotency-Key` per attempt and reuses it on network retries; the same key returns the same track instead of paying for a second generation. The same key with a different body is `422 IDEMPOTENCY_KEY_REUSED`. "Try again" after a failure is a new attempt with a new key.
- **Daily limit** per user (`DAILY_GENERATIONS_PER_USER`, UTC days), checked under a per-user lock so parallel requests cannot slip past it. Mock failures refused up front (rejected prompt, rate limit, outage, no credits) do not count. Live `QUOTA_EXCEEDED` and `PROVIDER_AUTH` refusals return the group credit only when every variant was definitively refused. Other live failures retain it, including timeouts, interruptions and unknown outcomes, since the song may have been generated and paid for. Deleting a track alone does not give the generation back. `GET /v1/me/quota` shows what is left.
- **Global paid-generation budget** (`DAILY_PAID_REQUESTS_TOTAL`, UTC days). This is a conservative admission budget, not an exact billing reconciliation or a credit-price cap.
  - *What it counts.* One unit per ElevenLabs variant, plus one for each newly reserved reference upload, independently of the per-user group credit. A new reference with `variants: 2` needs three units; later batches reuse its upload reservation and remote song identity. Replays of the same idempotency key do not reserve again. Mock work does not consume it. Upload reservations are counted despite the historical `reserved_variants` column name.
  - *How it is admitted.* Upload and variant reservations are admitted atomically before queueing: reservation and track insertion share one transaction, with the global advisory lock acquired before the user lock. Exhaustion returns `429 SERVICE_DAILY_LIMIT` with `error.details.resetsAt` (the next UTC midnight).
  - *What gives a unit back.* A paid worker's explicit `QUOTA_EXCEEDED`, `PROVIDER_AUTH` or local pre-upload `SERVICE_DAILY_LIMIT` refusal atomically fails the running variant and returns exactly its one unit to the original admission day, under the same global lock. Each newly admitted paid variant stores that reservation day on its own track row, and refunds use this durable marker, never the current adapter or its name. Duplicate completions cannot refund twice, and a partially refused group keeps the reservations of its other variants.
  - *Uploads.* A confirmed upload credit or auth rejection resets its attempt marker and returns exactly its proven upload reservation; an uncertain upload stays blocked. A queued sibling that follows a confirmed refusal must acquire a fresh upload reservation under the same global cap before calling ElevenLabs again. A successful upload stays spent even if composition is then refused.
  - *Everything else keeps its reservation:* all other outcomes, track deletion and account deletion. If an account purge already removed an in-flight row before its refusal was recorded, that reservation is conservatively kept. The usage ledger has no account foreign key or identifying user data.
  - *Configuration.* The cap is optional with dev sign-in and required whenever Firebase accounts can reach ElevenLabs. Paid reservations are recorded even while the cap is unset, so enabling it during the day includes earlier admissions; unset means no global rejection.
  - *Older rows.* Migration backfills existing ElevenLabs rows, including failed and deleted ones; usage of accounts purged before the migration cannot be reconstructed. Existing rows keep a null reservation marker because their provenance is unknown, so they never release global units, even if they fail after upgrading, and earlier failures are not refunded retroactively. Migration 0010 leaves legacy upload reservation markers null rather than guessing refundable provenance.
- **Retries and timeout.** Mock retryable failures use 2, 4 and 8 second delays. Live paid calls are not automatically retried, including 429/5xx and uncertain responses. Each song gives up after `GENERATION_TIMEOUT_MS`.
- **Audio** is served with `Range` support, so the player can seek without downloading the whole file again. It is kept in `AUDIO_DIR` on the server's disk, so run **one** instance with a persistent volume; several instances need an object store behind `AudioStorage`.

| Endpoint | What it does |
|---|---|
| `POST /v1/tracks` | Queue a generation: `prompt`, `lengthMs` (omit for Auto), `instrumental`, `modelId`. Needs accepted current terms; age/name onboarding is not required |
| `GET /v1/tracks?limit&cursor` | The user's tracks, newest first, a page at a time |
| `GET /v1/tracks/:id` | One track, with the timing of every sung word |
| `GET /v1/tracks/:id/audio` | The audio file (WAV for mock, MP3 for ElevenLabs); `409 NOT_READY` until it is done |
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

## Deploy

Use two Railway environments, `development` and `production`. Each needs its own backend service, Postgres service and audio volume; share neither database credentials nor audio storage between environments. A Firebase project may be shared, with identity deletion disabled in development. Both hosted environments use `NODE_ENV=production` and Firebase authentication: the environment name `development` does not make public development-token sign-in safe.

The Dockerfile uses Node.js 24 slim, installs from the lockfile, compiles TypeScript and copies only production dependencies, the package manifest, `dist/` and `drizzle/` into the runtime stage. The application runs as `node` (UID/GID 1000) with `node dist/main.js` as PID 1. Migrations are applied before the HTTP listener starts; `dist/core/db/database.js` resolves the bundled `/app/drizzle` directory relative to its module URL. `SIGTERM` reaches Node directly for graceful shutdown.

From the repository root, build without publishing:

```sh
docker build --tag studio-backend:local backend
```

### Service settings

For each backend service, set Root Directory to `/backend`, use the Dockerfile builder and the `Dockerfile` in that build context, and keep one replica. If the service supports the checked-in configuration file, set Config File Path to `/backend/railway.json`: its location does not follow Root Directory. See [Railway's monorepo settings](https://docs.railway.com/deployments/monorepo).

`railway.json` is the deployment specification. For a new service, also set the same values in the dashboard rather than assuming the file applies automatically: Dockerfile build, healthcheck `/health`, healthcheck timeout 300 seconds, restart `On Failure` with at most 10 retries, and one replica. Leave application Build/Start command overrides empty so the image's `CMD` runs. See the [configuration reference](https://docs.railway.com/config-as-code/reference) and [restart policy](https://docs.railway.com/deployments/restart-policy).

Generate a public HTTPS domain for each backend and use it as that environment's `PUBLIC_BASE_URL`. The server binds to `HOST=0.0.0.0` and reads Railway's injected `PORT`; do not pin the deployed port to the local default. Railway uses that port for its startup healthcheck. A successful check gates deployment traffic, rather than providing continuous uptime monitoring. See [Railway healthchecks](https://docs.railway.com/deployments/healthchecks).

### Environment variables

These are placeholders and setup defaults. Store actual credentials in Railway variables, never in this repository or Docker build arguments. Replace `Postgres` in the reference with the database service's actual name, and copy the generated HTTPS domains from the corresponding environment.

| Variable | `development` | `production` during setup |
|---|---|---|
| `NODE_ENV` | `production` | `production` |
| `HOST` | `0.0.0.0` | `0.0.0.0` |
| `PORT` | Railway-provided | Railway-provided |
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` from development Postgres | `${{Postgres.DATABASE_URL}}` from production Postgres |
| `AUDIO_DIR` | `/data/audio` on development volume | `/data/audio` on production volume |
| `PUBLIC_BASE_URL` | `https://<development-domain>.up.railway.app` | `https://<production-domain>.up.railway.app` |
| `AUTH_MODE` | `firebase` | `firebase` |
| `FIREBASE_SERVICE_ACCOUNT` | `<Firebase Admin service-account JSON>` | `<Firebase Admin service-account JSON>` |
| `FIREBASE_DELETE_USERS` | `false` | `true` |
| `MUSIC_PROVIDER` | `mock` | `mock` |
| `ELEVENLABS_API_KEY` | Unset | Unset during setup |
| `ELEVENLABS_ENABLE_REFERENCES` | `true` | `true` |
| `GENERATION_ALLOWED_EMAILS` | Unset: every signed-in tester can generate with the mock | `someone@example.com` |
| `DAILY_GENERATIONS_PER_USER` | `5` | `5` |
| `DAILY_PAID_REQUESTS_TOTAL` | `10` (mock does not consume it) | `10` (mock does not consume it) |
| `GENERATION_CONCURRENCY` | `1` | `1` |
| `LOG_LEVEL` | `info` | `info` |
| `LOG_PRETTY` | `false` | `false` |

`FIREBASE_SERVICE_ACCOUNT_FILE` is an alternative only when a credential file is securely mounted in the running container; it is not copied into the image. Keep `ALLOW_LAN_DEV_AUTH` unset or `false`. Leave `RAILWAY_RUN_UID` unset during application operation so the image's non-root user remains in effect.

Keep both environments on `MUSIC_PROVIDER=mock` for initial deployment and acceptance. Enabling live generation later is a separate production change: set `MUSIC_PROVIDER=elevenlabs`, supply the server-side API key, confirm the verified-email allowlist and both daily limits, then follow [LIVE_SETUP.md](LIVE_SETUP.md).

### Audio volume and permissions

Attach a separate persistent volume to each backend at `/data`. Keep one backend replica per environment because local audio is not shared object storage. Railway mounts volumes only at runtime, as root; a Dockerfile `chown` affects the image directory, not the mounted volume, and pre-deploy commands cannot prepare it. See [Railway volume availability and permissions](https://docs.railway.com/volumes).

Before the first non-root application deployment on each fresh volume, provision its audio directory with a one-time maintenance start:

1. On the correct environment's backend service, temporarily set `RAILWAY_RUN_UID=0` and replace the Start Command with the following command. It prepares storage and exits; it never starts the API as root.

   ```sh
   sh -c 'mkdir -p /data/audio && chown 1000:1000 /data/audio && chmod 750 /data/audio'
   ```

2. Run that maintenance deployment with the volume attached and confirm the preparation command exits successfully. It does not serve `/health`, so this is a storage bootstrap, not a healthy application deployment. Do it before routing application traffic to the fresh service.
3. Remove the `RAILWAY_RUN_UID` variable and the temporary Start Command. Deploy again using the Dockerfile's `USER node` and `CMD`. Leave healthcheck `/health` enabled for this actual application deployment.

The image contains no `/data` directory, and the API checks at startup that `AUDIO_DIR` is writable and exits otherwise. A missing volume, a wrong mount path or a skipped bootstrap therefore fails the deployment's healthcheck instead of the first paid generation. The bootstrap operates only on `/data/audio`; it does not reset or wipe a volume. Repeat the procedure separately for development and production. If the platform cannot run this maintenance command with the volume attached, arrange equivalent ownership through an operator-controlled maintenance container before deployment; do not work around it by leaving the API running as root.

A redeploy stops generations in flight: a paid one becomes `PROVIDER_OUTCOME_UNKNOWN` although ElevenLabs may already have charged for it. Turn off automatic deploys for production and deploy it by hand when nothing is generating.

### Acceptance

After configuring the variables, storage ownership and public domain, deploy development with the mock provider. Check `GET https://<development-domain>.up.railway.app/health` returns `{"ok":true}` and authenticated `/v1/me` works with a Firebase ID token. Confirm generation persists audio across an application restart and account deletion in development leaves the shared Firebase identity intact. Deploy production separately with the mock and its own database/volume before considering live generation.

## API contract

After Firebase/Google sign-in, GET /v1/me returns a provider/default display name. Accept the displayed terms with PATCH /v1/me {"acceptedTermsVersion":"2026-10-14"}. Age confirmation and name onboarding are not required. Existing age-consent fields remain backward compatible.

POST /v1/tracks requires Idempotency-Key (8–100 characters) and accepts:

```json
{"prompt":"A bright electronic melody","instrumental":false,"variants":2,"quality":"auto","lengthMs":30000,"modelId":"music_v2_5","customLyrics":"Hello Kyiv tonight"}
```

The transport accepts auto/deep/fast and persists the option; the mock does not change rendering quality. Live ElevenLabs supports Auto only and rejects Deep/Fast with UNSUPPORTED_MODE before queue insertion. Optional reference fields: referenceUploadId (owned upload UUID), remixTrackId (catalog ID or owned ready track UUID). Remix-of-remix is rejected. The response preserves the first track DTO and adds generationId, variantIndex, variants, and tracks containing all variants. Two variants reserve ONE daily generation atomically; the default limit is 25. Repeating the same key/body returns the same group; changing the body returns 422. A new key is an intentional new attempt. For PROVIDER_OUTCOME_UNKNOWN, inspect ElevenLabs history before creating another paid request.

POST /v1/uploads stores raw audio up to 20 MiB and returns {id,contentType,sizeBytes,durationMs:null,provider,expiresAt}. Mock accepts audio/mpeg, audio/mp4, audio/wav, audio/x-wav, audio/aac, audio/ogg and audio/webm; live mode checks MP3, WAV and MP4/M4A container signatures. Per-account limits are 20 files and 100 MiB, with 24-hour expiry and ownership-safe DELETE /v1/uploads/:id. Active generation references cannot be deleted. Live references are on by default and ELEVENLABS_ENABLE_REFERENCES=false turns them off; the worker transfers audio through the billable ElevenLabs upload endpoint, validates its extracted-plan duration and persists remote identity/attempt state. See LIVE_SETUP.md for codec inspection, entitlement and remote retention limits.

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

Playlist DTO: {id,name,createdAt,trackIds}. Track IDs match the 28-track Android built-in demo catalog. Likes, playlists and uploads persist in the database and are deleted with the account. Deleting a generated track also removes its library references.

The mock generates audible PCM WAV of exactly the requested duration (Auto:30 seconds), maps supplied words to synthetic lyric timings, and varies melodies between variants. Audio endpoints return Content-Type:audio/wav and support byte ranges. These are deterministic demo tones, not ElevenLabs music or synthesized singing. Remix/upload contracts are stored; the mock does not interpret or transform reference audio.

PATCH /v1/tracks/:id accepts {title:string} (trimmed, 1–80 characters) and returns the persisted track DTO. Only owned done/failed tracks can be renamed; queued/running returns 409 NOT_READY, foreign/deleted returns 404. This prevents a finishing provider from overwriting an in-flight rename.

Idempotency-Key values reserve the substring `:variant:` for internal variant rows; client keys containing it are rejected with 400. Deleted queued tracks are never sent to the provider. Library membership additions lock the referenced owned track in the same transaction as insertion, so concurrent track deletion cannot leave orphan references.

Account deletion uses a durable deletionRequestedAt marker (migration 0003). Other requests receive 409 ACCOUNT_DELETING while DELETE /v1/me remains retryable. Tombstoned tracks cannot save new audio; workers hold a track row lock through audio save and completion, so cleanup either waits for that save or prevents it. Single-track DELETE also marks first and can retry failed file cleanup after the track is hidden. A startup/30-second deletion worker retries marked users independently of client authentication. Catalog audio links honor PUBLIC_BASE_URL; HEAD synthesizes no full WAV and Range renders only the requested samples. GET /v1/capabilities describes provider modes and upload limits.

## Persistent recording Drafts

Owner-scoped recordings now support idempotent raw uploads, list/rename/delete and private byte-range playback, independently of temporary reference uploads. See [the recording contract](RECORDINGS.md) for limits, recovery, retention and client integration.

## Ordered remix sources

Mock generations accept up to four ordered originals with owner/original/readiness checks, durable identity and idempotency; live currently permits one source and rejects larger requests before billing. [Contract and limitations](REMIX.md).

Paid result persistence retries only local writes. If persistence still fails, any stored audio is retained by track ID and the generation is marked `PROVIDER_OUTCOME_UNKNOWN`, disabling another paid Retry. A lost completion acknowledgement is idempotent and cannot delete an already committed song. Permanent persistence failures require operator recovery of the existing result.
