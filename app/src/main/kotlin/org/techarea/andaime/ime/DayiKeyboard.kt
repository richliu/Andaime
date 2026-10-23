package org.techarea.andaime.ime

import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.KeyEvent
import kotlin.math.roundToInt

/** What the letter area types: plain English, Dayi radicals, or the 符號 layer (digits, symbols, editing keys). */
enum class InputLayer { ENGLISH, DAYI, SYMBOLS }

interface KeyboardActions {
    fun onKey(key: Char)
    fun onSpace()
    fun onBackspace()
    fun onEnter()
    fun onTab()
    fun onShift()
    fun onSelect(indexOnPage: Int)
    fun onNextPage()
    fun onPreviousPage()
    fun onToggleEnglish()
    fun onToggleSettings()
    fun onHide()
    fun onLayoutChange(layout: KeyboardLayout)
    fun onOpenDictionary()
    fun onSwitchIme()
    fun onVoiceInput()
    /** The 英文 / 大易 / 符號 keys. */
    fun onInputLayer(layer: InputLayer)
    /** Types [text] as is (symbol layer), bypassing Dayi composition. */
    fun onLiteral(text: String)
    /** Sends an editing key (arrows, Home, End, Delete...) to the app. */
    fun onEditKey(keyCode: Int)
    fun onExpand()
    /** Shows or hides the emoji panel in place of the keys. */
    fun onToggleEmoji()
    fun onEmojiTab(tab: Int)
    /** Types an emoji or 顏文字; [remember] adds it to 最近. */
    fun onEmoji(text: String, remember: Boolean)
    /** 輸出簡體 on/off. */
    fun onToggleSimplified()
}

private enum class Fn { BACKSPACE, TAB, CAPS, ENTER, SHIFT, SWITCH_IME, SPACE, HIDE, ENGLISH, DAYI, SYMBOLS }

/** The 104 editing block: Android key code plus a text label or an icon. */
private enum class Edit(val keyCode: Int, val label: String?, val icon: ImageVector? = null, val description: String) {
    INSERT(KeyEvent.KEYCODE_INSERT, "Ins", description = "Insert"),
    HOME(KeyEvent.KEYCODE_MOVE_HOME, "Home", description = "Home"),
    PAGE_UP(KeyEvent.KEYCODE_PAGE_UP, "PgUp", description = "Page Up"),
    DELETE(KeyEvent.KEYCODE_FORWARD_DEL, "Del", description = "Delete"),
    END(KeyEvent.KEYCODE_MOVE_END, "End", description = "End"),
    PAGE_DOWN(KeyEvent.KEYCODE_PAGE_DOWN, "PgDn", description = "Page Down"),
    UP(KeyEvent.KEYCODE_DPAD_UP, null, KeyIcons.ArrowUp, "上"),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN, null, KeyIcons.ArrowDown, "下"),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT, null, KeyIcons.ArrowLeft, "左"),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, null, KeyIcons.ArrowRight, "右"),
}

/** A key and its width in key units (a letter key is 1). */
private sealed interface KeySpec {
    val units: Float
}

private data class CharKey(val char: Char, override val units: Float = 1f) : KeySpec
private data class FnKey(val fn: Fn, override val units: Float) : KeySpec
/** Symbol-layer key: types its character as is. */
private data class LiteralKey(val char: Char, override val units: Float = 1f) : KeySpec
private data class EditKey(val edit: Edit, override val units: Float = 1f) : KeySpec
/** Empty space that keeps the arrow keys in their inverted-T shape. */
private data class Gap(override val units: Float = 1f) : KeySpec

/** One keyboard row, split where the hands split for touch typing. */
private class KeyRow(val left: List<KeySpec>, val right: List<KeySpec>)

private fun chars(s: String): List<KeySpec> = s.map { CharKey(it) }

/** The 104-key main block: every row adds up to 15 units, like a desktop keyboard. */
private val mainRows = listOf(
    KeyRow(chars("`12345"), chars("67890-=") + FnKey(Fn.BACKSPACE, 2f)),
    KeyRow(listOf(FnKey(Fn.TAB, 1.5f)) + chars("qwert"), chars("yuiop[]") + CharKey('\\', 1.5f)),
    KeyRow(listOf(FnKey(Fn.CAPS, 1.75f)) + chars("asdfg"), chars("hjkl;'") + FnKey(Fn.ENTER, 2.25f)),
    KeyRow(listOf(FnKey(Fn.SHIFT, 2.25f)) + chars("zxcvb"), chars("nm,./") + FnKey(Fn.SHIFT, 2.75f)),
)
private fun literals(s: String): List<KeySpec> = s.map { LiteralKey(it) }

/**
 * ?123 layer: digits and ASCII symbols on the left, the 104 editing block
 * (Ins/Home/PgUp, Del/End/PgDn, inverted-T arrows) on the right. Rows are 15
 * units like [mainRows]; the editing block goes with the right hand when split.
 */
private val symbolRows = listOf(
    KeyRow(
        literals("12345"),
        literals("67890") + FnKey(Fn.BACKSPACE, 2f) + EditKey(Edit.INSERT) + EditKey(Edit.HOME) + EditKey(Edit.PAGE_UP),
    ),
    KeyRow(
        literals("@#$%^&"),
        literals("*()-_+") + EditKey(Edit.DELETE) + EditKey(Edit.END) + EditKey(Edit.PAGE_DOWN),
    ),
    KeyRow(
        literals("!\"':;"),
        literals("/?=`~") + FnKey(Fn.ENTER, 2f) + Gap() + EditKey(Edit.UP) + Gap(),
    ),
    KeyRow(
        listOf(FnKey(Fn.TAB, 2f)) + literals("<>[]"),
        literals("{}\\|,.") + EditKey(Edit.LEFT) + EditKey(Edit.DOWN) + EditKey(Edit.RIGHT),
    ),
)
private const val ROW_UNITS = 15f
private const val SIDE_KEY_UNITS = 1.5f
/** 英文, 大易 and 符號 at the bottom left: which keyboard is showing. */
private const val MODE_KEY_UNITS = 1.25f
private val modeKeys = listOf(Fn.ENGLISH, Fn.DAYI, Fn.SYMBOLS).map { FnKey(it, MODE_KEY_UNITS) }

/** Width of the wider half across [rows], which sets the key unit in split mode. */
private fun halfUnits(rows: List<KeyRow>) =
    rows.maxOf { row -> maxOf(row.left.sumOf { it.units.toDouble() }, row.right.sumOf { it.units.toDouble() }) }.toFloat()

@Composable
fun DayiKeyboard(
    state: KeyboardUiState,
    actions: KeyboardActions,
    onBoundsChanged: (Rect) -> Unit,
) {
    val keyNames = state.keyNames
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        // Android 15+ makes our window edge-to-edge (we target 35+), so it reaches under the nav bar.
        // Older versions already stop above it; padding there would leave an empty strip.
        val edgeToEdge = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        val root = if (edgeToEdge) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars) else Modifier.fillMaxSize()
        BoxWithConstraints(root) {
            val layout = state.layout
            val reportBounds = Modifier.onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                onBoundsChanged(Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()))
            }
            when (layout.mode) {
                KeyboardMode.DOCKED -> KeyboardPanel(
                    state, keyNames, actions,
                    width = maxWidth * layout.dockedWidth,
                    splitGap = null,
                    modifier = Modifier.align(Alignment.BottomCenter).then(reportBounds),
                )
                KeyboardMode.SPLIT -> KeyboardPanel(
                    state, keyNames, actions,
                    width = maxWidth,
                    splitGap = maxWidth * layout.splitGap,
                    modifier = Modifier.align(Alignment.BottomCenter).then(reportBounds),
                )
                KeyboardMode.FLOATING -> FloatingKeyboard(
                    state, keyNames, actions,
                    screenWidth = maxWidth,
                    screenHeight = maxHeight,
                    reportBounds = reportBounds,
                )
            }
        }
    }
}

@Composable
private fun FloatingKeyboard(
    state: KeyboardUiState,
    keyNames: Map<Char, String>,
    actions: KeyboardActions,
    screenWidth: Dp,
    screenHeight: Dp,
    reportBounds: Modifier,
) {
    val layout = state.layout
    val density = LocalDensity.current
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    // Live position while dragging; saved as fractions of the free space when the drag ends
    var dragPos by remember { mutableStateOf<Offset?>(null) }

    val freeX = (with(density) { screenWidth.toPx() } - panelSize.width).coerceAtLeast(0f)
    val freeY = (with(density) { screenHeight.toPx() } - panelSize.height).coerceAtLeast(0f)
    val x = (dragPos?.x ?: (layout.floatingX * freeX)).coerceIn(0f, freeX)
    val y = (dragPos?.y ?: (layout.floatingY * freeY)).coerceIn(0f, freeY)

    val current by rememberUpdatedState(Triple(Offset(x, y), Offset(freeX, freeY), layout))
    val dragHandle = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = {
                val (pos, free, l) = current
                actions.onLayoutChange(
                    l.copy(
                        floatingX = if (free.x > 0f) pos.x / free.x else 0.5f,
                        floatingY = if (free.y > 0f) pos.y / free.y else 1f,
                    ),
                )
                dragPos = null
            },
        ) { change, amount ->
            change.consume()
            val (pos, free, _) = current
            dragPos = Offset((pos.x + amount.x).coerceIn(0f, free.x), (pos.y + amount.y).coerceIn(0f, free.y))
        }
    }

    KeyboardPanel(
        state, keyNames, actions,
        width = screenWidth * layout.floatingWidth,
        splitGap = null,
        dragHandle = dragHandle,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { panelSize = it }
            .then(reportBounds),
    )
}

@Composable
private fun KeyboardPanel(
    state: KeyboardUiState,
    keyNames: Map<Char, String>,
    actions: KeyboardActions,
    width: Dp,
    splitGap: Dp?,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
) {
    val floating = dragHandle != null
    Surface(
        modifier = modifier.width(width),
        shape = if (floating) RoundedCornerShape(12.dp) else RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (floating) 8.dp else 0.dp,
    ) {
        Column(Modifier.padding(4.dp)) {
            if (dragHandle != null) DragHandle(dragHandle)
            if (state.toolbarOnly) {
                HardwareKeyboardToolbar(state, actions)
            } else {
                // Top row: candidates, then 設定 second from the right and voice input at the far right
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CandidateBar(state, actions) }
                    TopRowKeys(state, actions)
                }
            }
            if (state.showSettings) {
                KeyboardSettings(state.layout, state.tables, actions::onLayoutChange, actions::onOpenDictionary)
            }
            if (state.emoji) {
                EmojiPanel(state, actions)
            } else if (!state.toolbarOnly) {
                KeyGrid(state, keyNames, actions, width = width - 8.dp, splitGap = splitGap)
            }
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 48.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

/** One page of candidates, each with the key that picks it (空 ' [ ] - \\), like PIME. */
@Composable
private fun CandidateBar(state: KeyboardUiState, actions: KeyboardActions) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.tableName == null && !state.english) {
            Text(
                "還沒有碼表，點這裡匯入（例如從 PIME 下載 .cin）",
                color = colors.primary,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = actions::onOpenDictionary).padding(8.dp),
            )
            return@Row
        }
        Text(
            state.composition,
            fontSize = 18.sp,
            color = colors.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(state.candidates) { index, candidate ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { actions.onSelect(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.selectionLabels.getOrNull(index)?.let { label ->
                        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(candidate, fontSize = 22.sp)
                }
            }
        }
        if (state.pageCount > 1) {
            PageButton("‹", enabled = state.page > 0, onClick = actions::onPreviousPage)
            Text("${state.page + 1}/${state.pageCount}", fontSize = 12.sp, color = colors.onSurfaceVariant)
            PageButton("›", enabled = state.page < state.pageCount - 1, onClick = actions::onNextPage)
        }
    }
}

/** With a hardware keyboard: candidates plus the options that have no physical key. */
@Composable
private fun HardwareKeyboardToolbar(state: KeyboardUiState, actions: KeyboardActions) {
    val height = 40.dp
    val width = 72.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { CandidateBar(state, actions) }
        val language = (if (state.english) "英" else "中") + if (state.fullShape) "・全" else ""
        KeyCap(width, height, language, null, function = true, active = state.english, onClick = actions::onToggleEnglish)
        KeyCap(width, height, "鍵盤", null, function = true, onClick = actions::onExpand)
        KeyCap(width, height, "切換", null, function = true, onClick = actions::onSwitchIme)
        KeyCap(width, height, "收起", null, function = true, onClick = actions::onHide)
        TopRowKeys(state, actions)
    }
}

/** Emoji, 繁/簡, 設定, then voice input: always the last keys of the top row, in both keyboard and toolbar mode. */
@Composable
private fun TopRowKeys(state: KeyboardUiState, actions: KeyboardActions) {
    val height = 40.dp
    val width = 72.dp
    IconKey(width, height, KeyIcons.Emoji, "表情符號", active = state.emoji, colored = true, onClick = actions::onToggleEmoji)
    SimplifiedKey(width, height, state.layout.outputSimplified, actions::onToggleSimplified)
    IconKey(width, height, KeyIcons.Settings, "設定", active = state.showSettings, onClick = actions::onToggleSettings)
    IconKey(width, height, KeyIcons.Mic, "語音輸入", onClick = actions::onVoiceInput)
}

/** A function key showing an icon; [description] is what screen readers announce. */
@Composable
private fun IconKey(
    width: Dp,
    height: Dp,
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    /** Keep the icon's own colours instead of the theme's text colour. */
    colored: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .width(width)
            .height(height)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) colors.primaryContainer else colors.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                colored -> Color.Unspecified
                active -> colors.onPrimaryContainer
                else -> colors.onSurface
            },
            modifier = Modifier.size(height * 0.55f),
        )
    }
}

/** 繁/簡: a green light in the top-left corner while committed text is converted to Simplified. */
@Composable
private fun SimplifiedKey(width: Dp, height: Dp, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .width(width)
            .height(height)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (on) "輸出簡體：開" else "輸出簡體：關" },
        contentAlignment = Alignment.Center,
    ) {
        Text("繁/簡", fontSize = 15.sp, color = colors.onSurface)
        if (on) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759)),
            )
        }
    }
}

@Composable
private fun PageButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 24.sp,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
    )
}

@Composable
private fun KeyGrid(
    state: KeyboardUiState,
    keyNames: Map<Char, String>,
    actions: KeyboardActions,
    width: Dp,
    splitGap: Dp?,
) {
    val keyHeight = state.layout.keyHeightDp.dp
    @Composable
    fun Keys(keys: List<KeySpec>, unit: Dp) {
        for (key in keys) KeyView(key, unit * key.units, keyHeight, state, keyNames, actions)
    }

    val rows = if (state.symbols) symbolRows else mainRows
    if (splitGap == null) {
        val unit = width / ROW_UNITS
        for (row in rows) {
            Row { Keys(row.left + row.right, unit) }
        }
        Row {
            Keys(
                modeKeys + listOf(
                    FnKey(Fn.SWITCH_IME, SIDE_KEY_UNITS),
                    FnKey(Fn.SPACE, ROW_UNITS - 3 * MODE_KEY_UNITS - 2 * SIDE_KEY_UNITS),
                    FnKey(Fn.HIDE, SIDE_KEY_UNITS),
                ),
                unit,
            )
        }
    } else {
        // Each half is anchored to its outer edge so thumbs reach the keys from the sides
        val halfUnits = halfUnits(rows)
        val half = (width - splitGap) / 2
        val unit = half / halfUnits
        val bottom = KeyRow(
            modeKeys + listOf(
                FnKey(Fn.SWITCH_IME, SIDE_KEY_UNITS),
                FnKey(Fn.SPACE, halfUnits - 3 * MODE_KEY_UNITS - SIDE_KEY_UNITS),
            ),
            listOf(FnKey(Fn.SPACE, halfUnits - SIDE_KEY_UNITS), FnKey(Fn.HIDE, SIDE_KEY_UNITS)),
        )
        for (row in rows + bottom) {
            Row {
                Row(Modifier.width(half)) { Keys(row.left, unit) }
                Spacer(Modifier.width(splitGap))
                Row(Modifier.width(half), horizontalArrangement = Arrangement.End) { Keys(row.right, unit) }
            }
        }
    }
}

@Composable
private fun KeyView(
    key: KeySpec,
    width: Dp,
    height: Dp,
    state: KeyboardUiState,
    keyNames: Map<Char, String>,
    actions: KeyboardActions,
) {
    when (key) {
        is CharKey -> {
            val letter = (if (state.shift) shifted(key.char) else key.char.uppercaseChar()).toString()
            val radical = keyNames[key.char]?.takeIf { it.isNotBlank() }
            val (main, hint) = when {
                radical == null || state.english || state.shift -> letter to null
                state.layout.labelStyle == LabelStyle.LETTER -> letter to radical
                else -> radical to letter
            }
            KeyCap(width, height, main, hint, onClick = { actions.onKey(key.char) })
        }
        is FnKey -> {
            val (label, hint, active, onClick) = when (key.fn) {
                Fn.BACKSPACE -> FnLabel("Backspace", null, false, actions::onBackspace)
                Fn.TAB -> FnLabel("Tab", null, false, actions::onTab)
                Fn.CAPS -> FnLabel(if (state.english) "英" else "中", "Caps", state.english, actions::onToggleEnglish)
                Fn.ENTER -> FnLabel("Enter", null, false, actions::onEnter)
                Fn.SHIFT -> FnLabel("Shift", null, state.shift, actions::onShift)
                Fn.SWITCH_IME -> FnLabel("切換", null, false, actions::onSwitchIme)
                Fn.SPACE -> FnLabel(
                    (if (state.english) "English" else state.tableName ?: "Andaime") + if (state.fullShape) "・全形" else "",
                    null,
                    state.fullShape,
                    actions::onSpace,
                )
                Fn.HIDE -> FnLabel(if (state.expanded) "收合" else "收起", null, false, actions::onHide)
                Fn.ENGLISH -> FnLabel("英文", null, state.english && !state.symbols) { actions.onInputLayer(InputLayer.ENGLISH) }
                Fn.DAYI -> FnLabel("大易", null, !state.english && !state.symbols) { actions.onInputLayer(InputLayer.DAYI) }
                Fn.SYMBOLS -> FnLabel("符號", null, state.symbols) { actions.onInputLayer(InputLayer.SYMBOLS) }
            }
            KeyCap(width, height, label, hint, function = true, active = active, onClick = onClick)
        }
        is LiteralKey -> KeyCap(width, height, key.char.toString(), null, onClick = { actions.onLiteral(key.char.toString()) })
        is EditKey -> {
            val icon = key.edit.icon
            if (icon != null) {
                IconKey(width, height, icon, key.edit.description, onClick = { actions.onEditKey(key.edit.keyCode) })
            } else {
                KeyCap(width, height, key.edit.label.orEmpty(), null, function = true, onClick = { actions.onEditKey(key.edit.keyCode) })
            }
        }
        is Gap -> Spacer(Modifier.width(width).height(height))
    }
}

private data class FnLabel(val label: String, val hint: String?, val active: Boolean, val onClick: () -> Unit)

@Composable
internal fun KeyCap(
    width: Dp,
    height: Dp,
    main: String,
    hint: String?,
    function: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    // Labels scale with the key size; narrow keys (split/floating) use their width so labels don't collide
    val base = if (function) height else minOf(height, width)
    val mainSize = with(density) { (base * if (function) 0.28f else 0.44f).toSp() }
    val hintSize = with(density) { (base * 0.24f).toSp() }
    Box(
        Modifier
            .width(width)
            .height(height)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    active -> colors.primaryContainer
                    function -> colors.surfaceContainerHigh
                    else -> colors.surfaceContainerHighest
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val textColor = if (active) colors.onPrimaryContainer else colors.onSurface
        if (function) {
            // Labels like "Home" or "Backspace" shrink to fit narrow keys (split mode) instead of clipping
            BasicText(
                main,
                maxLines = 1,
                style = TextStyle(color = textColor),
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = mainSize),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            Text(
                main,
                fontSize = mainSize,
                maxLines = 1,
                color = textColor,
                // Leave the top-right corner to the hint on letter keys
                modifier = if (hint != null) Modifier.offset(x = -width * 0.12f) else Modifier,
            )
        }
        if (hint != null) {
            Text(
                hint,
                fontSize = hintSize,
                maxLines = 1,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 4.dp),
            )
        }
    }
}
