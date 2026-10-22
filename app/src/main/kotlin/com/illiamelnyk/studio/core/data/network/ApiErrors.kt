package com.illiamelnyk.studio.core.data.network

import com.illiamelnyk.studio.core.domain.AppError

internal fun serverAppError(code: String, suggestion: String? = null): AppError = when (code) {
    "INVALID_PROMPT", "PROMPT_REJECTED", "VALIDATION" -> AppError.InvalidPrompt(suggestion)
    "DAILY_LIMIT" -> AppError.QuotaExceeded
    "QUOTA_EXCEEDED", "PROVIDER_AUTH", "PROVIDER_UNAVAILABLE" -> AppError.ProviderUnavailable
    "RATE_LIMITED" -> AppError.RateLimited
    "GENERATION_NOT_ALLOWED" -> AppError.GenerationNotAllowed
    "SERVICE_DAILY_LIMIT" -> AppError.ServiceDailyLimit
    "UNAUTHORIZED", "ACCOUNT_DELETED", "ACCOUNT_DISABLED" -> AppError.Unauthorized
    "TIMEOUT", "GENERATION_TIMEOUT" -> AppError.Timeout
    "UNSUPPORTED_MODE" -> AppError.UnsupportedMode
    "INVALID_REFERENCE", "REMIX_OF_REMIX", "NOT_READY", "TRACK_NOT_FOUND" -> AppError.InvalidReference
    "PROVIDER_OUTCOME_UNKNOWN" -> AppError.ProviderOutcomeUnknown
    else -> AppError.Server
}
