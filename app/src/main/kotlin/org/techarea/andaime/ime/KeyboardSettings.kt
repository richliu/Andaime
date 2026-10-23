package org.techarea.andaime.ime

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Inline panel above the keys, so size changes preview on the real keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettings(
    layout: KeyboardLayout,
    tables: List<InstalledTable>,
    onChange: (KeyboardLayout) -> Unit,
    onOpenDictionary: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tables.isEmpty()) {
                Text("碼表", Modifier.width(72.dp))
                Text("還沒有碼表")
            } else {
                ChoiceRow(
                    title = "碼表",
                    options = tables.map { it.id to it.name },
                    selected = tables.firstOrNull { it.id == layout.tableId }?.id ?: tables.first().id,
                ) { onChange(layout.copy(tableId = it)) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenDictionary) { Text("碼表與詞庫") }
        }

        ChoiceRow(
            title = "模式",
            options = listOf(KeyboardMode.DOCKED to "全寬", KeyboardMode.SPLIT to "分離", KeyboardMode.FLOATING to "浮動"),
            selected = layout.mode,
        ) { onChange(layout.copy(mode = it)) }

        ChoiceRow(
            title = "實體鍵盤",
            options = listOf(HardwareKeyboardMode.TOOLBAR to "只顯示工具列", HardwareKeyboardMode.FULL to "顯示完整鍵盤"),
            selected = layout.hardwareKeyboardMode,
        ) { onChange(layout.copy(hardwareKeyboardMode = it)) }

        // Four chips: scroll sideways on a narrow (split/floating) keyboard instead of clipping
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("中英切換", Modifier.width(72.dp))
            FilterChip(
                selected = layout.shiftTogglesLanguage,
                onClick = { onChange(layout.copy(shiftTogglesLanguage = !layout.shiftTogglesLanguage)) },
                label = { Text("快速按 Shift 切換") },
            )
            FilterChip(
                selected = layout.ctrlSpaceTogglesLanguage,
                onClick = { onChange(layout.copy(ctrlSpaceTogglesLanguage = !layout.ctrlSpaceTogglesLanguage)) },
                label = { Text("Ctrl+空白切換") },
            )
            FilterChip(
                selected = layout.graveCancels,
                onClick = { onChange(layout.copy(graveCancels = !layout.graveCancels)) },
                label = { Text("組字時 ` 鍵當 Esc") },
            )
            FilterChip(
                selected = layout.startInEnglish,
                onClick = { onChange(layout.copy(startInEnglish = !layout.startInEnglish)) },
                label = { Text("切到此輸入法時預設英文") },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("輸入", Modifier.width(72.dp))
            FilterChip(
                selected = layout.wildcard,
                onClick = { onChange(layout.copy(wildcard = !layout.wildcard)) },
                label = { Text("＊ 萬用字元查詢") },
            )
            Text("` 再按 M 開選單，`U 加十六進位輸入萬國碼，Shift+空白切全形")
        }

        ChoiceRow(
            title = "按鍵字",
            options = listOf(LabelStyle.LETTER to "英文大", LabelStyle.RADICAL to "字根大"),
            selected = layout.labelStyle,
        ) { onChange(layout.copy(labelStyle = it)) }

        SliderRow("按鍵高度", layout.keyHeightDp, KeyboardLayout.keyHeightRange, "${layout.keyHeightDp.roundToInt()} dp") {
            onChange(layout.copy(keyHeightDp = it))
        }

        when (layout.mode) {
            KeyboardMode.DOCKED -> SliderRow("寬度", layout.dockedWidth, KeyboardLayout.dockedWidthRange, percent(layout.dockedWidth)) {
                onChange(layout.copy(dockedWidth = it))
            }
            KeyboardMode.SPLIT -> SliderRow("中間空隙", layout.splitGap, KeyboardLayout.splitGapRange, percent(layout.splitGap)) {
                onChange(layout.copy(splitGap = it))
            }
            KeyboardMode.FLOATING -> SliderRow("寬度", layout.floatingWidth, KeyboardLayout.floatingWidthRange, percent(layout.floatingWidth)) {
                onChange(layout.copy(floatingWidth = it))
            }
        }
    }
}

private fun percent(fraction: Float) = "${(fraction * 100).roundToInt()}%"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.width(72.dp))
        for ((value, label) in options) {
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.width(72.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(valueText, Modifier.width(64.dp).padding(start = 8.dp))
    }
}
