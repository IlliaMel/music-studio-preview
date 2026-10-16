# Android Google / Firebase authentication

This adapter uses the Credential Manager Google button flow, exchanges the Google ID token with Firebase Authentication, and sends Firebase ID tokens to the Studio backend. The app session JSON stores only `firebase:<uid>` references. Firebase owns its private SDK session storage; Google ID tokens, Firebase ID tokens and refresh tokens are never written to Studio JSON or application logs.

The SDK app is named `studio-auth` and initializes lazily. `FirebaseInitProvider` is removed from the merged manifest. The default debug local-server build keeps the development adapter and never initializes Firebase. There is no product offline/demo CTA.

## Configuration

Register Android package `com.illiamelnyk.studio` in the same Firebase project used by the backend. Enable the Google sign-in provider and register the SHA-1 / SHA-256 fingerprints for each signing certificate, including the Play app-signing certificate for Play distribution. Create/use the OAuth **Web application** client ID, not the Android client ID.

Supply these public Android configuration values through Gradle properties (for example a local, untracked Gradle user properties file or CI properties):

```properties
studio.apiBaseUrl=https://your-studio-api.example
studio.authMode=firebase
studio.firebaseAppId=1:PROJECT_NUMBER:android:ANDROID_APP_IDENTIFIER
studio.firebaseApiKey=YOUR_FIREBASE_ANDROID_API_KEY
studio.firebaseProjectId=YOUR_FIREBASE_PROJECT_ID
studio.googleWebClientId=YOUR_WEB_OAUTH_CLIENT_ID.apps.googleusercontent.com
```

The first three Firebase values correspond to `mobilesdk_app_id`, `current_key`, and `project_id` in the registered Android app's `google-services.json`. The Web client ID is its OAuth client with `client_type: 3`. No Google services Gradle plugin or JSON file is required by this implementation: these values build explicit `FirebaseOptions`. Do not put an Admin service account, private key, client secret or backend provider key in the Android app. Configure the backend Admin SDK separately with the same Firebase project.

Missing/blank Firebase values or a disabled/unavailable API fail closed. Release never uses development authentication. The existing release signing setup still needs a production signing configuration before publication; supplying Firebase values does not make the APK ready for release.

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

## Validation and merge gates

Source and fake SDK/journal tests do not establish real provider acceptance. Before release, run a merged debug/release compile, lint, DI verification and unit suite; inspect the merged manifest for absence of Firebase auto-initialization. Verify default local-server startup performance still uses no Firebase SDK work. With a configured test project and Google Play-capable device, verify chooser cancel/rotation, sign-in, UID-matched token refresh, sign-out, deletion and cross-owner isolation. No real account login or paid provider call was performed while implementing this adapter.

Dependencies were selected from official documentation checked on 2026-10-16: Firebase BoM 35.0.0 (Auth 25.0.0), Credential Manager stable 1.6.0, Google ID 1.2.1.

Official sources: [Firebase Google authentication](https://firebase.google.com/docs/auth/android/google-signin), [Firebase Android setup and libraries](https://firebase.google.com/docs/android/setup), [Credential Manager implementation](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Credentials releases](https://developer.android.com/jetpack/androidx/releases/credentials), [Google ID releases](https://developers.google.com/identity/android-credential-manager/releases), [Firebase token expiry API](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/GetTokenResult).
