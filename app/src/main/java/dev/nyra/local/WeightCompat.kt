package dev.nyra.local

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fallback used only by extracted composables that are no longer direct ColumnScope children.
 * RowScope/ColumnScope member extensions still take precedence where true layout weight exists.
 *
 * This keeps the central chat viewport bounded until ChatPage is moved behind a dedicated
 * scaffold slot, avoiding an unbounded LazyColumn while preserving space for avatar + composer.
 */
fun Modifier.weight(weight: Float): Modifier {
    require(weight > 0f)
    return fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)
}
