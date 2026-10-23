package org.techarea.andaime.dictionary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.techarea.andaime.engine.LayeredDictionary
import org.techarea.andaime.engine.CinTable
import org.techarea.andaime.engine.UserDictionary
import org.techarea.andaime.ime.InstalledTable
import org.techarea.andaime.ime.TableRepository

/** Edits are stored per table as the user's layer on top of the imported .cin, which is never modified. */
private const val MAX_RESULTS = 200

private data class Entry(val code: String, val candidates: List<String>, val modified: Boolean)

/** 詞庫: look up codes or characters in a table and change, reorder, add or remove candidates. */
@Composable
fun DictionaryTab(tables: List<InstalledTable>, initialId: String?) {
    val context = LocalContext.current
    if (tables.isEmpty()) {
        Hint("還沒有碼表。先到「碼表」分頁匯入一個 .cin。")
        return
    }
    var tableId by remember { mutableStateOf(initialId) }
    val table = tables.firstOrNull { it.id == tableId } ?: tables.first()
    var query by remember { mutableStateOf("") }
    var onlyModified by remember { mutableStateOf(false) }
    // Bumped after every save so the list re-reads the dictionary
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val dict = remember(table.id) { TableRepository.get(context, table.id) } ?: return
    val entries = remember(dict, query, onlyModified, revision) { search(context, table, dict, query.trim(), onlyModified) }

    Column(Modifier.padding(horizontal = 16.dp).fillMaxSize()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in tables) {
                    FilterChip(selected = t.id == table.id, onClick = { tableId = t.id }, label = { Text(t.name) })
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("字碼（例如 /db.）或中文字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyModified, onClick = { onlyModified = !onlyModified }, label = { Text("只看已修改") })
                Spacer(Modifier.weight(1f))
                Button(onClick = { creating = true }) { Text("新增字碼") }
            }
            // Read revision so the count follows saves, imports and clears
            EditsTransfer(table, editCount = revision.let { dict.user.entries.size }, onChanged = { revision++ })
            when {
                entries == null -> Hint("輸入字碼查候選字，或輸入中文字查它的字碼。")
                entries.isEmpty() -> Hint("找不到符合的項目。")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.code }) { entry ->
                        EntryRow(entry, radicals(dict, entry.code)) { editing = entry.code }
                        HorizontalDivider()
                    }
                }
            }
    }

    val onSaved: () -> Unit = { revision++ }
    editing?.let { code ->
        EditEntryDialog(table, dict, code, isNew = false, onDismiss = { editing = null }, onSaved = onSaved)
    }
    if (creating) {
        EditEntryDialog(table, dict, code = query.trim().lowercase().takeIf(::isCode) ?: "", isNew = true,
            onDismiss = { creating = false }, onSaved = onSaved)
    }
}

/**
 * Moves a table's edits between devices or into a text editor: export them,
 * import a file (replacing all current edits, after confirmation), or clear them.
 */
@Composable
private fun EditsTransfer(table: InstalledTable, editCount: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { TableRepository.exportUserEdits(context, table.id, it) }
        }.isSuccess
        message = if (ok) "已匯出" to "可以在別的裝置匯入，或用文字編輯器修改後再匯入。" else "匯出失敗" to "無法寫入這個位置。"
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pending = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (pending == null) message = "無法匯入" to "讀不到這個檔案。"
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("「${table.name}」的修改：$editCount 筆", fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedButton(enabled = editCount > 0, onClick = { export.launch("andaime-詞庫-${table.name}.txt") }) { Text("匯出") }
        OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("匯入") }
        OutlinedButton(enabled = editCount > 0, onClick = { clearing = true }) { Text("清除全部") }
    }

    pending?.let { bytes ->
        val incoming = remember(bytes) { UserDictionary.read(CinTable.decode(bytes).reader()).entries.size }
        if (incoming == 0) {
            MessageDialog("無法匯入", "檔案裡沒有「字碼<Tab>字 字…」格式的行。") { pending = null }
        } else {
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text("匯入到「${table.name}」？") },
                text = { Text("檔案有 $incoming 筆修改，會取代這個碼表目前的 $editCount 筆修改。") },
                confirmButton = {
                    TextButton(onClick = {
                        TableRepository.importUserEdits(context, table.id, bytes)
                        pending = null
                        onChanged()
                    }) { Text("匯入") }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
            )
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("清除「${table.name}」的全部修改？") },
            text = { Text("$editCount 筆修改都會刪除，回到碼表原本的內容。建議先匯出備份。") },
            confirmButton = {
                TextButton(onClick = {
                    TableRepository.clearUserEdits(context, table.id)
                    clearing = false
                    onChanged()
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("取消") } },
        )
    }
    message?.let { (title, text) -> MessageDialog(title, text) { message = null } }
}

@Composable
internal fun Hint(text: String) {
    Text(text, Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Codes are typed on the Latin keys; anything else is treated as text to reverse-look-up. */
private fun isCode(s: String) = s.isNotEmpty() && s.all { it.code in 0x21..0x7e }

internal fun radicals(dict: LayeredDictionary, code: String) =
    code.map { dict.keyNames[it] ?: it.toString() }.joinToString("")

/** null means "nothing asked yet". */
private fun search(
    context: android.content.Context,
    table: InstalledTable,
    dict: LayeredDictionary,
    query: String,
    onlyModified: Boolean,
): List<Entry>? {
    fun entry(code: String) = Entry(code, dict.lookup(code), dict.user[code] != null)

    if (onlyModified) {
        return dict.user.entries.keys
            .filter { it.startsWith(query.lowercase()) || !isCode(query) && query in dict.user.entries.getValue(it) }
            .sorted().map(::entry)
    }
    if (query.isEmpty()) return null
    if (isCode(query)) {
        val prefix = query.lowercase()
        val codes = ((dict.base as CinTable).codes.asSequence() + dict.user.entries.keys.asSequence())
            .filter { it.startsWith(prefix) }
            .distinct()
            .sortedWith(compareBy({ it.length }, { it }))
            .take(MAX_RESULTS)
        return codes.map(::entry).toList()
    }
    // Whole query first (phrases), then each character
    val texts = (listOf(query) + query.map { it.toString() }).distinct()
    return texts.flatMap(dict::codesOf).distinct().take(MAX_RESULTS).map(::entry)
}

@Composable
private fun EntryRow(entry: Entry, radicals: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(120.dp)) {
            Text(entry.code, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            Text(radicals, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (entry.candidates.isEmpty()) "（已刪除）" else entry.candidates.joinToString("  "),
            fontSize = 20.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        if (entry.modified) {
            Text("已修改", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EditEntryDialog(
    table: InstalledTable,
    dict: LayeredDictionary,
    code: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var codeText by remember { mutableStateOf(code) }
    val items = remember(code) { dict.lookup(code).toMutableStateList() }
    var addText by remember { mutableStateOf("") }
    val shipped = dict.base.lookup(codeText.lowercase())
    val hasUserVersion = dict.user[codeText.lowercase()] != null

    fun save() {
        val c = codeText.trim().lowercase()
        if (!isCode(c)) return
        dict.user.set(c, items.toList())
        TableRepository.saveUserEdits(context, table.id)
        onSaved()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (isNew) {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    label = { Text("字碼") },
                    singleLine = true,
                )
            } else {
                Text("${table.name}　$code　${radicals(dict, code)}")
            }
        },
        text = {
            Column {
                if (shipped.isNotEmpty()) {
                    Text("原始：${shipped.joinToString(" ")}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(items) { index, value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, fontSize = 22.sp, modifier = Modifier.weight(1f))
                            TextButton(enabled = index > 0, onClick = { items.add(index - 1, items.removeAt(index)) }) { Text("上移") }
                            TextButton(enabled = index < items.lastIndex, onClick = { items.add(index + 1, items.removeAt(index)) }) { Text("下移") }
                            TextButton(onClick = { items.removeAt(index) }) { Text("刪除") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = addText,
                        onValueChange = { addText = it },
                        label = { Text("新增字詞（空白分隔）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        addText.split(' ').filter { it.isNotBlank() && it !in items }.forEach(items::add)
                        addText = ""
                    }) { Text("加入") }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("儲存") } },
        dismissButton = {
            Row {
                if (hasUserVersion) {
                    TextButton(onClick = {
                        dict.user.reset(codeText.lowercase())
                        TableRepository.saveUserEdits(context, table.id)
                        onSaved()
                        onDismiss()
                    }) { Text("還原預設") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
