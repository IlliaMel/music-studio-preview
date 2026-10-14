package com.illiamelnyk.studio.feature.create.domain

/**
 * A place in a tool's prompt that the user fills in with one of [options], such as the new genre of
 * Genreshift. [label] stands in the prompt until then.
 */
data class ToolSlot(val id: String, val label: String, val options: List<String>) {
    init {
        require(options.isNotEmpty()) { "Slot $id offers no options" }
    }
}

/**
 * What a tool asks the service for: fixed text with [slots] in between. The text is a prompt for
 * the Music API, in English as the service expects it, so it is content rather than UI copy.
 */
class ToolTemplate private constructor(val parts: List<Part>) {

    sealed interface Part {
        data class Text(val text: String) : Part

        data class Slot(val slot: ToolSlot) : Part
    }

    val slots: List<ToolSlot> get() = parts.mapNotNull { (it as? Part.Slot)?.slot }

    /** The prompt with every slot replaced by its value in [values], or `null` while one is missing. */
    fun fill(values: Map<String, String>): String? = buildString {
        for (part in parts) {
            when (part) {
                is Part.Text -> append(part.text)
                is Part.Slot -> append(values[part.slot.id] ?: return null)
            }
        }
    }

    companion object {
        private val PLACEHOLDER = Regex("""\{(\w+)\}""")

        /**
         * A template from [text] in which `{id}` marks where the slot with that id goes. Every
         * placeholder needs one of [slots], and every slot a placeholder.
         */
        fun of(text: String, vararg slots: ToolSlot): ToolTemplate {
            val byId = slots.associateBy { it.id }
            val parts = mutableListOf<Part>()
            var end = 0
            for (match in PLACEHOLDER.findAll(text)) {
                if (match.range.first > end) parts += Part.Text(text.substring(end, match.range.first))
                val id = match.groupValues[1]
                parts += Part.Slot(requireNotNull(byId[id]) { "No slot for {$id}" })
                end = match.range.last + 1
            }
            if (end < text.length) parts += Part.Text(text.substring(end))
            val placed = parts.mapNotNull { (it as? Part.Slot)?.slot?.id }.toSet()
            require(placed == byId.keys) { "Slots without a placeholder: ${byId.keys - placed}" }
            return ToolTemplate(parts)
        }
    }
}
