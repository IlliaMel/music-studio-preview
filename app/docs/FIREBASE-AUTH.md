# Android Google / Firebase authentication

This adapter uses the Credential Manager Google button flow, exchanges the Google ID token with Firebase Authentication, and sends Firebase ID tokens to the Studio backend. The app's own storage keeps only `firebase:<uid>` references. Firebase owns its private SDK session storage; Google ID tokens, Firebase ID tokens and refresh tokens are never written to Studio storage or application logs.

The SDK app is named `studio-auth` and initializes lazily. `FirebaseInitProvider` is removed from the merged manifest. A `devDebug` build without a development API URL keeps the development adapter and never initializes Firebase.

## Configuration

Register two Android apps in the Firebase project the backend uses: `com.illiamelnyk.studio` for the `prod` flavor and `com.illiamelnyk.studio.dev` for `dev`. Enable the Google sign-in provider and register the SHA-1 / SHA-256 fingerprints of every signing certificate on both apps: the debug key, the release key and, for Play distribution, the Play app-signing certificate. Use the OAuth **Web application** client ID, not the Android client ID.

The Gradle properties are listed under [Build environments](../../README.md#build-environments) in the README. They map to `google-services.json` like this:

| Gradle property | `google-services.json` field |
| --- | --- |
| `studio.dev.firebaseAppId`, `studio.prod.firebaseAppId` | `mobilesdk_app_id` of that flavor's app |
| `studio.firebaseApiKey` | `current_key` (shared by both apps) |
| `studio.firebaseProjectId` | `project_id` |
| `studio.googleWebClientId` | the OAuth client with `client_type: 3` |

No Google services Gradle plugin or JSON file is required by this implementation: these values build explicit `FirebaseOptions`. Do not put an Admin service account, private key, client secret or backend provider key in the Android app. Configure the backend Admin SDK separately with the same Firebase project.

Missing/blank Firebase values or a disabled/unavailable API fail closed. Release never uses development authentication. Release builds are signed only when all `studio.release.*` signing properties are set, as the README describes.

## Lifecycle and credentials

- Welcome requests credentials only after a user tap, with an Activity-based `MutableContextWrapper`. There is no global Activity reference. Request IDs reject stale credential callbacks; chooser dismissal is silent and releases the spinner.
- The Firebase token adapter requires the exact current SDK UID both before and after token resolution. Normal requests use `getIdToken(false)` so Firebase can reuse/refresh its cached ID token. An ID token within 30 seconds of expiry is explicitly refreshed once. No Google OAuth token is accepted as a backend bearer.
- Explicit sign-out clears the matching Firebase session and asks Credential Manager to clear its provider state. A provider-state cleanup failure is logged without credential values; the Firebase session has already been signed out.
- Cancellation before SDK authentication completes discards the abandoned SDK result. Cancellation after a Studio session was durably accepted preserves its matching SDK refresh source. A failed backend handshake without an accepted app session clears the SDK session.

## Pending deletion and switching accounts

The existing backend deletion sequence removes account audio/data, deletes the Firebase identity, then removes its database row. A lost DELETE response can therefore leave a local pending intent whose identity no longer exists.

Before replacing a retained SDK user A with a Google sign-in for B, the adapter retries A's pending deletion. A network error or an ordinary server 401 preserves A's SDK session and blocks replacement; Retry can recover after connectivity/server configuration is repaired. Generic 401, disabled identity and absent local SDK user are never treated as successful deletion.

For a pending deletion that returns Unauthorized, one forced SDK identity refresh distinguishes these bounded cases:

| Trusted SDK result | Local handling |
| --- | --- |
| `ERROR_USER_NOT_FOUND` | Keep the deletion receipt with `remoteCompletionUnverified=true`, purge local private data, clear A's SDK session, stop impossible automatic auth retries, allow B. This does **not** claim remote data deletion is complete. |
| `ERROR_USER_TOKEN_EXPIRED` / `ERROR_INVALID_USER_TOKEN` | Keep `requiresReauthentication=true`, stop automatic retries, allow B. Choosing A again reauthenticates its SDK session and retries deletion before any Studio session for A can open. |
| No matching local SDK session for A | Keep a reauthentication receipt; this proves only that the local refresh source is unavailable, never that A or its server data was deleted. |
| Network failure or generic Unauthorized | Keep the ordinary pending journal and available A refresh source; retry before replacing it. |

Unverified receipts retain UID references and deletion evidence. Usual lost-response cases are covered by the backend's durable deletion worker; externally removed Firebase identities or persistent server-auth failures can still require operator reconciliation. There is no unauthenticated remote deletion-status endpoint in this scope. These receipts must not be presented as “deleted everywhere.”

If reauthentication of A succeeds but its deletion retry then fails through the network, the adapter durably resets the reauthentication flag and retains A's newly recovered SDK refresh source. It blocks replacement by B until that retry succeeds or an explicit terminal/reauthentication condition is established. Receipt classification never skips incomplete local private-data cleanup after a crash.

## Dependencies

Firebase BoM 35.0.0 (Auth 25.0.0), Credential Manager 1.6.0, Google ID 1.2.1.

Official sources: [Firebase Google authentication](https://firebase.google.com/docs/auth/android/google-signin), [Firebase Android setup and libraries](https://firebase.google.com/docs/android/setup), [Credential Manager implementation](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Credentials releases](https://developer.android.com/jetpack/androidx/releases/credentials), [Google ID releases](https://developers.google.com/identity/android-credential-manager/releases), [Firebase token expiry API](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/GetTokenResult).
