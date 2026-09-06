package dev.nyra.local

/**
 * Keeps model-private reasoning markers out of chat history and the visible stream.
 * Some local models (notably Qwen thinking variants) emit <think>...</think> before the answer.
 * Nyra treats that region as an implementation detail and only presents the final response.
 */
object ModelTextSanitizer {
    fun visible(raw: String): String {
        if (raw.isEmpty()) return raw
        var text = raw
        var guard = 0
        while (guard++ < 32) {
            val start = text.indexOf("<think>", ignoreCase = true)
            if (start < 0) break
            val end = text.indexOf("</think>", startIndex = start + 7, ignoreCase = true)
            if (end < 0) {
                // Streaming chunk is still inside a reasoning block. Keep anything that appeared
                // before the block, but never leak the unfinished block into the UI.
                return clean(text.substring(0, start))
            }
            text = text.removeRange(start, end + 8)
        }
        text = text.replace("</think>", "", ignoreCase = true)
        return clean(text)
    }

    private fun clean(text: String): String = text
        .replace(Regex("^[\\s\\n\\r]+"), "")
        .replace(Regex("[ \\t]+\\n"), "\n")
}
