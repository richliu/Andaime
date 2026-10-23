package org.techarea.andaime.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class Notice(val name: String, val license: String, val note: String? = null)

private val notices = listOf(
    Notice("Andaime", "Apache License 2.0"),
    Notice("Kotlin 標準函式庫", "Apache License 2.0"),
    Notice("AndroidX（Activity、Lifecycle、SavedState）", "Apache License 2.0"),
    Notice("Jetpack Compose（UI、Foundation、Material 3）", "Apache License 2.0"),
    Notice("Material Design 圖示（設定、麥克風、表情）", "Apache License 2.0"),
    Notice("OpenCC 繁簡轉換字典（TSCharacters、TSPhrases）", "Apache License 2.0", "輸出簡體使用；© BYVoid 與 OpenCC 貢獻者"),
)

@Composable
fun AboutTab() {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Andaime", style = MaterialTheme.typography.headlineSmall)
        Text("andaime：葡萄牙文的「鷹架」。用字根把字一塊一塊搭起來。")
        Text("Andaime 沒有網路權限，不會把你打的字送到任何地方。碼表由你自己下載、匯入，存在這台裝置上。")
        Text("開放原始碼授權", style = MaterialTheme.typography.titleMedium)
        for (notice in notices) {
            Column {
                Text(notice.name)
                Text(notice.license, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                notice.note?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text(
            "匯入的碼表依各自的授權使用，Andaime 不附任何碼表。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
