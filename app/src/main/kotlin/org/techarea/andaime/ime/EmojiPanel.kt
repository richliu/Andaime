package org.techarea.andaime.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tab 0 of the emoji panel; the emoji groups follow, then 顏文字. */
const val RECENT_EMOJI_TAB = 0

/** Most recent first, without repeats, at most [max]. */
fun addRecent(recent: List<String>, item: String, max: Int = 40): List<String> =
    (listOf(item) + recent.filter { it != item }).take(max)

/**
 * Takes the place of the keys, like Gboard's emoji keyboard: a scrolling grid
 * for the chosen tab, and a bottom row with ABC (back to the keys), the tabs
 * (最近, each emoji group, 顏文字) and Backspace.
 */
@Composable
fun EmojiPanel(state: KeyboardUiState, actions: KeyboardActions) {
    val keyHeight = state.layout.keyHeightDp.dp
    val groups = state.emojiGroups
    val kaomojiTab = groups.size + 1
    val tab = state.emojiTab.coerceIn(RECENT_EMOJI_TAB, kaomojiTab)
    Column(Modifier.fillMaxWidth().height(keyHeight * KEY_ROWS)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // A new grid per tab, so switching tabs starts at the top
            key(tab) {
                when (tab) {
                    RECENT_EMOJI_TAB -> if (state.recentEmoji.isEmpty()) {
                        Text(
                            "還沒有最近使用的表情",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        EmojiGrid(state.recentEmoji) { actions.onEmoji(it, remember = true) }
                    }
                    kaomojiTab -> KaomojiGrid { actions.onEmoji(it, remember = false) }
                    else -> EmojiGrid(groups[tab - 1].items) { actions.onEmoji(it, remember = true) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyCap(SIDE_KEY_WIDTH, keyHeight, "ABC", null, function = true, onClick = actions::onToggleEmoji)
            LazyRow(Modifier.weight(1f)) {
                item { EmojiTab("最近", tab == RECENT_EMOJI_TAB, keyHeight) { actions.onEmojiTab(RECENT_EMOJI_TAB) } }
                items(groups.size) { i ->
                    EmojiTab(groups[i].items.first(), tab == i + 1, keyHeight) { actions.onEmojiTab(i + 1) }
                }
                item { EmojiTab("^_^", tab == kaomojiTab, keyHeight) { actions.onEmojiTab(kaomojiTab) } }
            }
            KeyCap(SIDE_KEY_WIDTH, keyHeight, "Backspace", null, function = true, onClick = actions::onBackspace)
        }
    }
}

@Composable
private fun EmojiGrid(items: List<String>, onPick: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.fillMaxSize()) {
        items(items) { emoji ->
            Box(
                Modifier.height(52.dp).clip(RoundedCornerShape(6.dp)).clickable { onPick(emoji) },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 28.sp) }
        }
    }
}

@Composable
private fun KaomojiGrid(onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize()) {
        for (group in kaomojiGroups) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(group.name, fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(8.dp, 6.dp))
            }
            items(group.items) { kaomoji ->
                Box(
                    Modifier.height(44.dp).padding(2.dp).clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceContainerHighest).clickable { onPick(kaomoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(kaomoji, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun EmojiTab(label: String, selected: Boolean, height: Dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .width(56.dp)
            .height(height)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Emoji tabs draw big; text tabs (最近, ^_^) stay at reading size
        val text = label.length > 2 || label.first().isLetter()
        Text(label, fontSize = if (text) 16.sp else 22.sp, maxLines = 1)
    }
}

/** Same height as the key grid: four rows of keys plus the space row. */
private const val KEY_ROWS = 5
private val SIDE_KEY_WIDTH = 96.dp
