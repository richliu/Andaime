package org.techarea.andaime.dictionary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.techarea.andaime.engine.LayeredDictionary
import org.techarea.andaime.engine.TypingCode
import org.techarea.andaime.engine.splitCharacters
import org.techarea.andaime.engine.typingCodes
import org.techarea.andaime.ime.InstalledTable
import org.techarea.andaime.ime.TableRepository

/** Longer text is cut here so a pasted article doesn't make a list of thousands. */
private const val MAX_CHARACTERS = 500

/** PIME 大易 selection keys, page by page: space then ' [ ] - \. */
private val PIME_SELECTION = listOf("空白", "'", "[", "]", "-", "\\")

/** 查碼: type or paste text and see how to type each character in the chosen table. */
@Composable
fun LookupTab(tables: List<InstalledTable>, initialId: String?) {
    val context = LocalContext.current
    if (tables.isEmpty()) {
        Hint("還沒有碼表。先到「碼表」分頁匯入一個 .cin。")
        return
    }
    var tableId by remember { mutableStateOf(initialId) }
    val table = tables.firstOrNull { it.id == tableId } ?: tables.first()
    var text by remember { mutableStateOf("") }
    val dict = remember(table.id) { TableRepository.get(context, table.id) } ?: return
    val characters = remember(text) { splitCharacters(text).take(MAX_CHARACTERS) }

    Column(Modifier.padding(horizontal = 16.dp).fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in tables) {
                FilterChip(selected = t.id == table.id, onClick = { tableId = t.id }, label = { Text(t.name) })
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("輸入或貼上文字") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        if (characters.isEmpty()) {
            Hint("每個字會列出它在「${table.name}」的字碼（字根與按鍵），短的在前，以及選字要按哪個鍵。")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(characters) { _, ch ->
                CharacterRow(ch, dict.typingCodes(ch), dict, table.pimeDayi)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CharacterRow(ch: String, codes: List<TypingCode>, dict: LayeredDictionary, pimeDayi: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(ch, fontSize = 30.sp, modifier = Modifier.width(56.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (codes.isEmpty()) {
                Text("碼表裡沒有這個字", color = colors.onSurfaceVariant)
            }
            for (c in codes) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(radicals(dict, c.code), fontSize = 20.sp)
                    Text(c.code, fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = colors.primary)
                    Text(selection(c.position, pimeDayi), fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Which key picks the candidate at [position], e.g. "按 '" or "第 2 頁按 ["; other tables just count. */
private fun selection(position: Int, pimeDayi: Boolean): String {
    if (!pimeDayi) return "第 ${position + 1} 個候選字"
    val page = position / PIME_SELECTION.size
    val key = PIME_SELECTION[position % PIME_SELECTION.size]
    return if (page == 0) "按 $key" else "第 ${page + 1} 頁按 $key"
}
