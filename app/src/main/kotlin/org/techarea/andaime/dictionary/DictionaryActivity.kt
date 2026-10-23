package org.techarea.andaime.dictionary

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.techarea.andaime.ime.ImportPreview
import org.techarea.andaime.ime.TableRepository

/**
 * The app's only screen: 碼表 (import / manage tables), 詞庫 (edit a table's
 * candidates), 查碼 (how to type a text) and 關於 (licences). Also the target for .cin files opened or
 * shared from a browser or file manager, so a downloaded table installs
 * without the app ever needing network access.
 */
class DictionaryActivity : ComponentActivity() {
    private var tab by mutableIntStateOf(0)
    private var pendingImport by mutableStateOf<ImportPreview?>(null)
    private var importError by mutableStateOf<String?>(null)
    /** Bumped when tables change so the tabs re-read the installed list. */
    private var revision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTable = intent.getStringExtra(EXTRA_TABLE)
        // Opened from the keyboard with no tables yet: start on the import tab
        tab = if (initialTable != null && TableRepository.installed(this).isNotEmpty()) TAB_DICTIONARY else TAB_TABLES
        handleIncoming(intent)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Screen(initialTable)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** ACTION_VIEW ("open with") and ACTION_SEND ("share") of a .cin file. */
    private fun handleIncoming(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        } ?: return
        tab = TAB_TABLES
        startImport(uri)
    }

    fun startImport(uri: Uri) {
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val data = input.readBytes()
                require(data.size <= MAX_TABLE_BYTES) { "too large" }
                data
            }
        }.getOrNull()
        val preview = bytes?.let { TableRepository.preview(it, displayName(uri)) }
        if (preview == null) {
            importError = "這個檔案讀不出碼表：需要是含有 %chardef 段落的 .cin 檔（UTF-8 或 Big5）。"
        } else {
            pendingImport = preview
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(initialTable: String?) {
        // Read revision so installs and deletes recompose the tabs
        val tables = revision.let { TableRepository.installed(this) }
        Scaffold(topBar = { TopAppBar(title = { Text("Andaime") }) }) { padding ->
            Column(Modifier.padding(padding)) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == TAB_TABLES, onClick = { tab = TAB_TABLES }, text = { Text("碼表") })
                    Tab(selected = tab == TAB_DICTIONARY, onClick = { tab = TAB_DICTIONARY }, text = { Text("詞庫") })
                    Tab(selected = tab == TAB_LOOKUP, onClick = { tab = TAB_LOOKUP }, text = { Text("查碼") })
                    Tab(selected = tab == TAB_ABOUT, onClick = { tab = TAB_ABOUT }, text = { Text("關於") })
                }
                when (tab) {
                    TAB_TABLES -> TablesTab(tables, onImport = ::startImport, onChanged = { revision++ })
                    TAB_DICTIONARY -> DictionaryTab(tables, initialTable)
                    TAB_LOOKUP -> LookupTab(tables, initialTable)
                    else -> AboutTab()
                }
            }
        }
        pendingImport?.let { preview ->
            ImportDialog(
                preview,
                onInstall = { name, pimeDayi ->
                    TableRepository.install(this, preview, name, pimeDayi)
                    pendingImport = null
                    revision++
                },
                onDismiss = { pendingImport = null },
            )
        }
        importError?.let { message -> MessageDialog("無法匯入", message) { importError = null } }
    }

    companion object {
        const val EXTRA_TABLE = "table"
        private const val TAB_TABLES = 0
        private const val TAB_DICTIONARY = 1
        private const val TAB_LOOKUP = 2
        private const val TAB_ABOUT = 3
        /** Real tables are well under 1 MB; refuse anything absurd before parsing. */
        private const val MAX_TABLE_BYTES = 32 * 1024 * 1024
    }
}
