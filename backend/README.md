# Studio backend

The server behind the Studio Android app. It signs users in with Firebase and keeps their profiles; next it will keep their tracks, hold the ElevenLabs API key away from the phone and run the long music generations, so the phone only has to poll for the result.

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

`requests.http` has the whole sign-in and onboarding flow for the HTTP client in Android Studio.

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
├─ main.ts           composition root: reads config, opens the database, creates every object, starts the server
├─ app.ts            createApp(deps): middleware, /health, feature routes under /v1, error handling
├─ core/             shared plumbing, no product rules
│  ├─ config.ts      environment → checked Config; the server refuses to start on a bad value
│  ├─ logger.ts      pino with secrets redacted
│  ├─ auth/          AuthProvider (Firebase or dev), the authenticate and requireOnboarding middleware
│  ├─ db/            database client (Postgres or PGlite), migrations, schema index
│  └─ http/          request context, error format, zod validation, request logging
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

The app signs in with Firebase (Google, email link; Apple later) and sends the Firebase ID token with every request as `Authorization: Bearer <token>`. The server never sees passwords and keeps no sessions:

1. `authenticate` checks the token's signature, expiry and audience with the Firebase Admin SDK, using Google's cached public keys.
2. `UsersService.resolve` finds our user by the Firebase uid, or creates them on their first request.
3. Routes read the user with `c.get('user')`. Routes that need a finished profile add `requireOnboarding`.

A token that is missing, malformed or expired gets `401 UNAUTHORIZED` with `WWW-Authenticate: Bearer`, and the app refreshes it or signs in again. Firebase being unreachable is a `500`, not a `401`, so the app does not sign the user out because of our outage.

| Endpoint | What it does |
|---|---|
| `GET /v1/me` | The profile and which onboarding steps are left: `displayName`, `age`, `terms` |
| `PATCH /v1/me` | Saves onboarding answers: `displayName`, `ageConfirmed: true`, `acceptedTermsVersion`, `marketingOptIn` |
| `DELETE /v1/me` | Deletes the account: our data first, then the Firebase user. Safe to retry |

When the terms change, bump `TERMS_VERSION` in `users.service.ts`: every user gets `terms` back in the missing steps and `requireOnboarding` stops them until they accept.

**Modes.** `AUTH_MODE=firebase` needs `FIREBASE_SERVICE_ACCOUNT`, the JSON key from Firebase console → Project settings → Service accounts, pasted as one line; the Admin SDK needs it to delete users. `AUTH_MODE=dev` accepts `dev:<name>` tokens, so tests, CI and anyone without our Firebase project can use the API. Because anyone could claim any name, the server refuses dev mode in production and on any address other than loopback.

## Adding a feature

Say, playlists:

1. **Table.** `src/features/playlists/playlists.table.ts` with `pgTable(...)`, then export it from `src/core/db/schema.ts`.
2. **Migration.** `npm run db:generate -- --name create_playlists` writes the SQL to `drizzle/`. Read it and commit it; the server applies it on the next start. CI fails if a schema change has no migration.
3. **Repository.** Typed queries in `playlists.repository.ts`; a wrong column name is a compile error.
4. **Service.** The rules in `playlists.service.ts`, with dependencies passed to the constructor.
5. **Routes.** `playlists.routes.ts` returns a `Hono<AppEnv>`; validate input with `validate('json', schema)` and read it with `c.req.valid('json')`.
6. **Register.** Create the feature in `main.ts` and add it to the `features` list.
7. **Tests.** `createTestDatabase()` gives a migrated in-memory Postgres; `truncateAll()` empties it between tests. Route tests call `app.request(...)`, without a network.

## Database

- **Locally and in tests:** PGlite, nothing to install and no Docker.
- **Deployed:** any Postgres through `DATABASE_URL` (for example Railway's). The server refuses to start in production without it.
- **Migrations** are plain SQL files generated from the schema and reviewed in pull requests. The server applies the pending ones at start, so a deploy is a single step.
