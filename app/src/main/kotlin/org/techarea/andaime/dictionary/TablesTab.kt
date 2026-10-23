package org.techarea.andaime.dictionary

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.techarea.andaime.ime.ImportPreview
import org.techarea.andaime.ime.InstalledTable
import org.techarea.andaime.ime.TableRepository

/** Where PIME keeps its .cin tables; users download from here until Andaime has its own page. */
const val PIME_TABLES_URL = "https://github.com/EasyIME/PIME/tree/master/python/cinbase/cin"

/** Andaime's own table page, once the public repo exists. Null hides the link's target and shows it as coming soon. */
val ANDAIME_TABLES_URL: String? = null

@Composable
fun TablesTab(tables: List<InstalledTable>, onImport: (Uri) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    // System file picker: no storage permission, works with Downloads, Drive, mail attachments...
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }
    var deleting by remember { mutableStateOf<InstalledTable?>(null) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tables.isEmpty()) {
            Text("Andaime 不附碼表。請先下載一個 .cin 碼表再匯入，例如 PIME 的大易三碼（dayi3.cin）或大易四碼（dayi4.cin）。")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("下載碼表", style = MaterialTheme.typography.titleMedium)
                Text(
                    "會用瀏覽器開啟。在 GitHub 點選 .cin 檔，再按右上角的「Download raw file」下載。" +
                        "下載完成後，點下載通知並選擇用 Andaime 開啟，或回到這裡按「從檔案匯入」。",
                    fontSize = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openBrowser(context, PIME_TABLES_URL) }) { Text("PIME 的碼表") }
                    val ours = ANDAIME_TABLES_URL
                    OutlinedButton(enabled = ours != null, onClick = { ours?.let { openBrowser(context, it) } }) {
                        Text(if (ours != null) "Andaime 的碼表" else "Andaime 的碼表（即將推出）")
                    }
                }
                Text(
                    "Andaime 沒有網路權限，碼表由瀏覽器下載，Andaime 只讀取你選的檔案。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("從檔案匯入 .cin") }

        SymbolTableCard()

        if (tables.isNotEmpty()) {
            Text("已安裝的碼表", style = MaterialTheme.typography.titleMedium)
            for (table in tables) {
                TableRow(
                    table,
                    onPimeDayiChange = { enabled ->
                        TableRepository.setPimeDayi(context, table.id, enabled)
                        onChanged()
                    },
                    onDelete = { deleting = table },
                )
                HorizontalDivider()
            }
        }
    }

    deleting?.let { table ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("刪除「${table.name}」？") },
            text = { Text("碼表和你在詞庫裡對它的修改都會一起刪除。") },
            confirmButton = {
                TextButton(onClick = {
                    TableRepository.delete(context, table.id)
                    deleting = null
                    onChanged()
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

/** 大易符號 (= + key): export to edit in any text editor, import it back, or return to the built-in list. */
@Composable
private fun SymbolTableCard() {
    val context = LocalContext.current
    var custom by remember { mutableStateOf(TableRepository.hasCustomSymbols(context)) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { TableRepository.exportSymbols(context, it) }
        }.isSuccess
        message = if (ok) "已匯出" to "可以用文字編輯器修改，再用「匯入」載入。" else "匯出失敗" to "無法寫入這個位置。"
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        val table = bytes?.let { TableRepository.importSymbols(context, it) }
        custom = TableRepository.hasCustomSymbols(context)
        message = if (table != null) {
            "已匯入" to "共 ${table.size} 個鍵，鍵盤下次叫出時生效。"
        } else {
            "無法匯入" to "檔案裡沒有「鍵<Tab>符號 符號…」格式的行。"
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("大易符號表", style = MaterialTheme.typography.titleMedium)
            Text(
                "先按 = 再按一個鍵出符號（例如 =, 出「，、‘’“”」）。可以匯出成文字檔修改，再匯入使用。" +
                    "目前使用：" + if (custom) "自訂的符號表" else "Andaime 內建的符號表",
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { export.launch("andaime-symbols.txt") }) { Text("匯出") }
                OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("匯入") }
                OutlinedButton(enabled = custom, onClick = {
                    TableRepository.resetSymbols(context)
                    custom = false
                    message = "已還原" to "改回 Andaime 內建的符號表。"
                }) { Text("還原預設") }
            }
        }
    }

    message?.let { (title, text) -> MessageDialog(title, text) { message = null } }
}

@Composable
private fun TableRow(table: InstalledTable, onPimeDayiChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(table.name, fontSize = 18.sp)
                Text(
                    "${table.codes} 個字碼，最長 ${table.maxCodeLength} 碼",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text("刪除") }
        }
        PimeDayiSwitch(table.pimeDayi, onPimeDayiChange)
    }
}

@Composable
private fun PimeDayiSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("用 PIME 大易的方式選字")
            Text(
                "空白鍵、' [ ] - \\ 選字，= 打大易符號。關閉則依碼表本身的設定（其他輸入法的碼表）。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(4.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ImportDialog(preview: ImportPreview, onInstall: (name: String, pimeDayi: Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember(preview) { mutableStateOf(preview.name) }
    var pimeDayi by remember(preview) { mutableStateOf(preview.looksDayi) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("匯入碼表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.fileName?.let { Text("檔案：$it", fontSize = 13.sp) }
                Text("${preview.table.size} 個字碼，最長 ${preview.table.maxCodeLength} 碼")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名稱") }, singleLine = true)
                PimeDayiSwitch(pimeDayi) { pimeDayi = it }
            }
        },
        confirmButton = { TextButton(onClick = { onInstall(name.trim(), pimeDayi) }) { Text("安裝") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("確定") } },
    )
}

private fun openBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser installed; nothing sensible to do
    }
}
