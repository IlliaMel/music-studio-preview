package com.illiamelnyk.studio.core.domain

/**
 * The result of an operation that can fail with an [AppError]. Unlike `kotlin.Result` it doesn't
 * need the error to be a `Throwable`, so errors stay plain values without stack traces.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>
}
