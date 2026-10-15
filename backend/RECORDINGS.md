# Persistent recording Drafts

Authenticated recording Drafts are separate from temporary generation references in /v1/uploads. Recordings have no automatic 24-hour expiry and consume no generation credits. They remain until explicitly deleted or the account is deleted. The API is server-backed; client-local recovery files are an implementation detail, not an offline product mode.

| Request | Contract |
| --- | --- |
| GET /v1/recordings | {recordings: Recording[]} for the current owner, newest first |
| PUT /v1/recordings/:id | Raw audio; client-generated UUID; Content-Type plus X-Recording-Title (percent-encoded UTF-8) and X-Recording-Duration-Ms |
| PATCH /v1/recordings/:id | JSON {title}; 1–80 trimmed characters |
| GET or HEAD /v1/recordings/:id/audio | Authenticated private audio; byte Range and Content-Length |
| DELETE /v1/recordings/:id | 204; repeats for the same owner are idempotent |

Recording JSON contains only id, title, contentType, sizeBytes, durationMs, createdAt, updatedAt and audioUrl. Audio URLs contain no token; every fetch requires the current owner bearer. Owner IDs, hashes and original upload metadata are not returned. Another owner's missing resource returns 404, including when recording UUIDs coincide across accounts. Local files use owner-and-recording namespaced storage keys distinct from generated tracks.

PUT returns 201 on first save and 200 for an identical retry. The persisted identity is original title, duration, MIME, size and SHA-256; a later rename does not change it. Retrying the original PUT returns the current renamed recording. Changed original payload returns 409 RECORDING_ID_REUSED. A deletion tombstone prevents delayed PUT from resurrecting audio and returns 409 RECORDING_DELETED. A new recording needs a new UUID.

Title uses URI percent encoding, not form encoding: Java URLEncoder spaces must replace plus with %20; literal plus is %2B. Duration is an integer from 1 to 600000 milliseconds. MP4/M4A, MP3 and WAV signatures are checked; this is bounded container validation, not a full media decoder. Duration is supplied by the recorder, not verified by decoding. Each owner may store 20 recordings and 100 MiB; individual audio is limited to 20 MiB. The same bounds are reported by /v1/capabilities. Quotas are independent of temporary uploads and generation credits.

A durable pending row is reserved before file save, so process death cannot leave audio untracked by account cleanup. Pending rows are omitted from the ready list, consume storage budget and are recoverable by the same original PUT or deletable by the known UUID. An unfinished reservation has a 24-hour lease from its last state update. Current-owner list and valid PUT requests reclaim stalled pending rows under the same owner lock: bytes must be deleted successfully before the row/quota is released. Cleanup failure returns a server error, keeps the reservation charged and can be retried. A reclaimed unfinished UUID may be submitted again; this never reopens an explicit user-deletion tombstone. READY recordings do not expire, including recordings older than 24 hours. This bounds abandoned hidden reservations when the original client journal is lost without treating temporary reference uploads as permanent Drafts. Save and account deletion share the owner advisory lock. Once delete marks the row, no completion can resurrect it. Failed file deletion retains its bytes/count against the budget until successful retry, rather than allowing unbounded hidden audio. Account deletion removes ready, pending and tombstoned recording files before Firebase and final cascading SQL deletion; the existing background recovery worker retries failures without a usable client token.

Migrations 0005_recording_drafts and 0006_recording_cleanup_budget add the durable metadata and cleanup-budget marker. Audio uses the existing persistent AudioStorage abstraction and volume. No external storage credentials or additional paid API calls are needed for local integration; production still requires durable storage and the independent Firebase/Postgres/ElevenLabs gates.

A recording is not directly a generation reference. Upload the chosen bytes through the separate /v1/uploads contract to get a temporary owner-scoped referenceUploadId; this keeps generation reference expiry separate from Draft retention.
