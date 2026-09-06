package dev.nyra.local

/** Byte budget is conservative, not a claim of exact tokenizer accounting. */
object ContextManager {
    data class Line(val role: String, val text: String)
    fun recent(lines: List<Line>, maxBytes: Int): List<Line> {
        require(maxBytes >= 0)
        var remaining = maxBytes
        return lines.asReversed().takeWhile {
            val size = it.text.toByteArray(Charsets.UTF_8).size + 48
            if (size <= remaining) { remaining -= size; true } else false
        }.asReversed()
    }
    fun relevant(query: String, memories: List<Memory>, max: Int = 5): List<Memory> {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        // Explicit lexical retrieval; never presented as semantic embeddings.
        return memories.map { it to (if (it.pinned) 100 else 0) + words.count { w -> it.text.contains(w, true) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(max).map { it.first }
    }
}
