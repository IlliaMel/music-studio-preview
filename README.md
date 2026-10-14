# Studio

An Android client for the [Eleven Music API](https://elevenlabs.io/docs/overview/capabilities/music):
describe a song, let it generate in the background, then listen with synced lyrics and share the file.

> **Unofficial prototype.** Not affiliated with, endorsed by, or connected to ElevenLabs.

Work in progress.

## Development

- `./gradlew check` runs the code checks: formatting (Spotless with ktlint), Android Lint and tests. CI runs it together with the debug and release builds.
- `./gradlew spotlessApply` fixes formatting. The rules live in `.editorconfig`, which Android Studio follows too.
- Optional: `./gradlew spotlessInstallGitPrePushHook` installs a hook that checks formatting before every push.
