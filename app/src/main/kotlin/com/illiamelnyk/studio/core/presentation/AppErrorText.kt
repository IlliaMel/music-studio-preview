package com.illiamelnyk.studio.core.presentation

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.AppError

/** What to tell the user about [this] error. */
fun AppError.toUiText(): UiText = when (this) {
    is AppError.InvalidPrompt -> if (suggestion == null) {
        UiText.Resource(R.string.error_invalid_prompt)
    } else {
        UiText.Resource(R.string.error_invalid_prompt_suggestion, suggestion)
    }

    AppError.QuotaExceeded -> UiText.Resource(R.string.error_quota_exceeded)

    AppError.RateLimited -> UiText.Resource(R.string.error_rate_limited)

    AppError.Unauthorized -> UiText.Resource(R.string.error_unauthorized)

    AppError.Network -> UiText.Resource(R.string.error_network)

    AppError.Timeout -> UiText.Resource(R.string.error_timeout)

    AppError.Server -> UiText.Resource(R.string.error_server)

    AppError.Unknown -> UiText.Resource(R.string.error_unknown)
}
