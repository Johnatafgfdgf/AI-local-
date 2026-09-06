package dev.nyra.local
import org.junit.Assert.*
import org.junit.Test
class ContextManagerTest {
    @Test fun preservesContiguousRecentHistoryWithinUtf8Budget() {
        val lines = listOf(ContextManager.Line("user", "old".repeat(100)), ContextManager.Line("user", "ação"), ContextManager.Line("model", "sim"))
        val selected = ContextManager.recent(lines, 110)
        assertEquals(lines.takeLast(2), selected)
        assertTrue(selected.sumOf { it.text.toByteArray().size + 48 } <= 110)
    }
    @Test fun oversizedLatestMessageDoesNotResurrectStaleContext() {
        assertEquals(emptyList<ContextManager.Line>(), ContextManager.recent(listOf(ContextManager.Line("user", "old"), ContextManager.Line("user", "x".repeat(1000))), 100))
    }
    @Test fun unrelatedMemoryIsExcludedUnlessPinned() {
        val unrelated = Memory(text="receita de bolo")
        val pinned = Memory(text="falar português", pinned=true)
        assertEquals(listOf(pinned), ContextManager.relevant("física", listOf(unrelated, pinned)))
    }
}
