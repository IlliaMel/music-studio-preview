package com.illiamelnyk.studio.core.domain.session

/**
 * A public name on Studio: trimmed and 1 to [MAX_LENGTH] characters, the rule the server enforces.
 * The only way to get one is [parse], so an invalid name can't reach a [ProfileUpdate].
 */
@JvmInline
value class DisplayName private constructor(val value: String) {

    /** The outcome of checking what the user typed. */
    sealed interface Parsed {
        data class Valid(val name: DisplayName) : Parsed

        /** Empty or only whitespace. */
        data object Blank : Parsed

        /** Longer than [MAX_LENGTH] after trimming. */
        data object TooLong : Parsed
    }

    companion object {
        /** Counted in UTF-16 units, like `String.length` here and in the server's validation. */
        const val MAX_LENGTH = 40

        fun parse(input: String): Parsed {
            val trimmed = input.trim()
            return when {
                trimmed.isEmpty() -> Parsed.Blank
                trimmed.length > MAX_LENGTH -> Parsed.TooLong
                else -> Parsed.Valid(DisplayName(trimmed))
            }
        }
    }
}
