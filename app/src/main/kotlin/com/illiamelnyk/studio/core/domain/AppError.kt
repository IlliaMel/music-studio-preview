package com.illiamelnyk.studio.core.domain

/**
 * Why an operation failed, in terms the app can act on. Repositories map HTTP statuses, error
 * codes and exceptions to these; the UI turns them into text and never sees the raw failure.
 */
sealed interface AppError {
    /**
     * The service refused the prompt, for example because it names an artist.
     *
     * @param suggestion a prompt the service would accept instead, when it offers one.
     */
    data class InvalidPrompt(val suggestion: String?) : AppError

    /** The daily generation limit is used up. */
    data object QuotaExceeded : AppError

    /** Too many requests in a short time; trying again a little later works. */
    data object RateLimited : AppError

    /**
     * The sign-in is no longer valid even after refreshing the token once: the user has to sign
     * in again.
     */
    data object Unauthorized : AppError

    /** No connection, or the connection dropped. */
    data object Network : AppError

    /** The server didn't answer in time. */
    data object Timeout : AppError

    /** The server failed (5xx). */
    data object Server : AppError

    data object UnsupportedMode : AppError

    data object InvalidReference : AppError

    data object ProviderOutcomeUnknown : AppError

    /** Anything else; the details go to the log, not to the user. */
    data object Unknown : AppError
}
