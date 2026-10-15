# Studio

An Android client for the [Eleven Music API](https://elevenlabs.io/docs/overview/capabilities/music):
describe a song, let it generate in the background, then listen with synced lyrics and share the file.

> **Unofficial prototype.** Not affiliated with, endorsed by, or connected to ElevenLabs.

The local build includes durable user data, grouped generation, reference recording/import, native background audio, lyrics, queues, likes, playlists and file export. Firebase sign-in and ElevenLabs generation remain mocked; this is not a production release.

## Run locally

Android requires JDK 17 or newer and the configured Android SDK. Build with ./gradlew assembleDebug. The debug Welcome screen offers **Continue with local server**, using http://10.0.2.2:8787 on the emulator. The ordinary demo path works without a server; release builds keep the demo until production authentication is configured.

In backend/, use Node.js 24+, npm ci, npm run check, npm run build and npm start. Defaults bind to loopback, use durable PGlite files and mock generation. See [backend setup and contracts](backend/README.md).

## Development

- `./gradlew check` runs the code checks: formatting (Spotless with ktlint), Android Lint and tests. CI runs it together with the debug and release builds.
- `./gradlew spotlessApply` fixes formatting. The rules live in `.editorconfig`, which Android Studio follows too.
- Optional: `./gradlew spotlessInstallGitPrePushHook` installs a hook that checks formatting before every push.
- Debug builds add a second launcher entry, **Studio UI Kit**: every component of the design system in both color schemes.

## Third-party

- [Inter](https://github.com/rsms/inter) by Rasmus Andersson, from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/inter): SIL Open Font License 1.1, in `app/src/main/assets/licenses/inter-OFL.txt`.
- [Material Symbols](https://github.com/google/material-design-icons) by Google, converted to vector drawables: Apache License 2.0, in `app/src/main/assets/licenses/material-symbols-LICENSE.txt`.
