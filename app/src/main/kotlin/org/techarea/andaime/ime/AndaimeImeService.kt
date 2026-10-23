package org.techarea.andaime.ime

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.techarea.andaime.dictionary.DictionaryActivity
import org.techarea.andaime.engine.ChineseConverter
import org.techarea.andaime.engine.CinTable
import org.techarea.andaime.engine.DecodeRules
import org.techarea.andaime.engine.FullShape
import org.techarea.andaime.engine.KeyResult
import org.techarea.andaime.engine.LayeredDictionary
import org.techarea.andaime.engine.TableEngine

/**
 * IME shell: owns the engine, forwards soft and hardware keys to it, and
 * hosts the Compose keyboard.
 *
 * InputMethodService is not an Activity, so it has to provide the lifecycle
 * and saved-state owners that ComposeView looks up on the view tree.
 *
 * The IME window always spans the full screen height with a transparent
 * background; [onComputeInsets] then reports only the keyboard's rectangle as
 * touchable, so a floating keyboard can sit anywhere and taps elsewhere reach
 * the app underneath.
 */
class AndaimeImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var engine: TableEngine
    private lateinit var layoutStore: KeyboardLayoutStore
    private val ui = KeyboardUiState()

    /** 中/英 the user last chose in an ordinary text field; English-only fields don't change it. */
    private var userEnglish = false
    private var fieldForcesEnglish = false

    /** PIME's ` prefix: `M function menu, `E emoji and `U Unicode input. */
    private val menu = FunctionMenu(
        root = { menuRoot(ui.layout.outputSimplified, { ui.layout.wildcard }, { ui.fullShape }, ::canDraw) },
        emoji = { emojiMenu(::canDraw) },
    )

    /** Emoji newer than the system font show as boxes; checked once per emoji. */
    private val glyphPaint = Paint()
    private val drawable = HashMap<String, Boolean>()

    private fun canDraw(text: String) = drawable.getOrPut(text) { glyphPaint.hasGlyph(text) }

    private val emojiPrefs by lazy { getSharedPreferences("emoji", MODE_PRIVATE) }

    private val shiftTap = ShiftTapDetector()
    /** Hardware keys whose press we consumed; their release is swallowed too so the app never sees half a key. */
    private val consumedKeys = HashSet<Int>()

    private var composeView: ComposeView? = null
    /** Keyboard rectangle in IME window coordinates, reported by the Compose layout. */
    private val keyboardBounds = Rect()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        layoutStore = KeyboardLayoutStore(this)
        ui.layout = layoutStore.load()
        loadTable()
        // The service is created each time the user switches to this keyboard
        userEnglish = ui.layout.startInEnglish
        ui.recentEmoji = emojiPrefs.getString(RECENT_EMOJI, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        // A first-time user has no 最近 yet, so start on the first group
        ui.emojiTab = if (ui.recentEmoji.isEmpty()) RECENT_EMOJI_TAB + 1 else RECENT_EMOJI_TAB
        updateHardwareKeyboard(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateHardwareKeyboard(newConfig)
    }

    private fun updateHardwareKeyboard(config: Configuration) {
        val connected = (config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) || debugAssumesHardwareKeyboard()
        if (connected != ui.hardwareKeyboard) {
            ui.hardwareKeyboard = connected
            ui.expanded = false
        }
    }

    /**
     * Debug builds only: `debugHardwareKeyboard` in the layout prefs pretends a
     * keyboard is connected, so the toolbar mode can be checked over adb.
     */
    private fun debugAssumesHardwareKeyboard(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && layoutStore.debugHardwareKeyboard()

    /**
     * Android hides the soft keyboard while a hardware keyboard is connected.
     * Keep our window so the candidate bar and option buttons stay reachable.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    /** The default refuses implicit show requests (tapping a field) when a hardware keyboard is present. */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean =
        if (ui.hardwareKeyboard) true else super.onShowInputRequested(flags, configChange)

    /** What [engine] was built from; a different value (import, delete, decoding switch) means rebuild. */
    private var loadedTable: InstalledTable? = null
    private var loadedDictionary: LayeredDictionary? = null
    private var loadedSymbolsVersion = -1
    private var loadedWildcard = false

    /**
     * Picks the chosen table, or the first installed one. With none installed
     * the engine has no codes, so every key types itself and the keyboard
     * shows a prompt to import a table.
     */
    private fun loadTable() {
        val tables = TableRepository.installed(this)
        val info = tables.firstOrNull { it.id == ui.layout.tableId } ?: tables.firstOrNull()
        ui.tables = tables
        val dictionary = info?.let { TableRepository.get(this, it.id) }
        val symbolsVersion = TableRepository.symbolsVersion
        if (::engine.isInitialized && info == loadedTable && dictionary === loadedDictionary &&
            symbolsVersion == loadedSymbolsVersion && ui.layout.wildcard == loadedWildcard
        ) return
        if (info == null || dictionary == null) {
            engine = TableEngine(CinTable.parse(emptySequence()), DecodeRules.pimeDayi(4))
            ui.tableName = null
            ui.keyNames = emptyMap()
        } else {
            val symbols = if (info.pimeDayi) TableRepository.symbols(this) else null
            val rules = TableRepository.rules(info, dictionary.base as CinTable)
            engine = TableEngine(dictionary, if (ui.layout.wildcard) rules else rules.copy(wildcard = null), symbols)
            ui.tableName = info.name
            ui.keyNames = dictionary.keyNames
        }
        loadedTable = info
        loadedDictionary = dictionary
        loadedSymbolsVersion = symbolsVersion
        loadedWildcard = ui.layout.wildcard
        syncUi()
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setContent {
                DayiKeyboard(
                    state = ui,
                    actions = keyboardActions,
                    onBoundsChanged = ::updateKeyboardBounds,
                )
            }
        }.also { composeView = it }
    }

    /** Runs for every field, with or without the soft keyboard (hardware keyboards too). */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (restarting) return
        resetComposition()
        fieldForcesEnglish = prefersEnglish(attribute?.inputType ?: 0)
        ui.english = fieldForcesEnglish || userEnglish
        ui.shift = false
        ui.symbols = false
        ui.emoji = false
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateHardwareKeyboard(resources.configuration)
        // Tables may have been imported, deleted or switched in the 碼表 screen meanwhile
        loadTable()
        expandWindowToFullHeight()
        moveLifecycle(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        resetComposition()
        ui.showSettings = false
        moveLifecycle(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        resetComposition()
        super.onFinishInput()
    }

    override fun onDestroy() {
        // super.onDestroy() can still call onFinishInputView (e.g. switching to voice input
        // while the keyboard is up), so the lifecycle is only destroyed after it returns
        super.onDestroy()
        moveLifecycle(Lifecycle.Event.ON_DESTROY)
        composeView = null
    }

    /** A destroyed lifecycle can't move again; late callbacks during teardown are ignored. */
    private fun moveLifecycle(event: Lifecycle.Event) {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) lifecycleRegistry.handleLifecycleEvent(event)
    }

    /** Landscape "extract" mode would hide the app behind a full-screen editor; tablets never want it. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val windowHeight = window.window?.decorView?.height ?: return
        if (keyboardBounds.isEmpty || windowHeight == 0) return

        // Docked/split push the app up to the keyboard's top edge; floating leaves the app full height
        val contentTop = if (ui.layout.mode == KeyboardMode.FLOATING) windowHeight else keyboardBounds.top
        outInsets.contentTopInsets = contentTop
        outInsets.visibleTopInsets = contentTop
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(keyboardBounds)
    }

    private fun expandWindowToFullHeight() {
        val w = window.window ?: return
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // android.R.id.inputArea is the frame InputMethodService puts the input view in
        listOfNotNull(w.findViewById<View>(android.R.id.inputArea), composeView).forEach { v ->
            v.layoutParams?.let { lp ->
                if (lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                    v.layoutParams = lp
                }
            }
        }
    }

    private fun updateKeyboardBounds(bounds: Rect) {
        if (bounds == keyboardBounds) return
        keyboardBounds.set(bounds)
        // Insets are recomputed on the next traversal; a drag only moves pixels, so ask for one
        composeView?.requestLayout()
    }

    private val keyboardActions = object : KeyboardActions {
        override fun onKey(key: Char) {
            if (menu.isActive) {
                ui.shift = false
                applyMenu(menu.onKey(key))
                return
            }
            if (ui.shift && engine.isSymbolMode) {
                // 大易符號 uses shifted keys as codes (=? =! =<), so feed the shifted character in
                ui.shift = false
                apply(engine.onKey(shifted(key)))
                return
            }
            if (ui.shift) {
                ui.shift = false
                // Shift+8 is the wildcard, as on a hardware keyboard
                val wildcard = !ui.english && shifted(key) == DecodeRules.WILDCARD
                val result = if (wildcard) engine.onKey(DecodeRules.WILDCARD) else KeyResult.Ignored
                if (result != KeyResult.Ignored) return apply(result)
                resetComposition()
                commit(shape(shifted(key).toString()))
                return
            }
            if (ui.english) {
                commit(shape(key.toString()))
                return
            }
            if (!engine.isComposing && menu.onKey(key) != MenuResult.Ignored) return syncUi()
            when (val result = engine.onKey(key)) {
                KeyResult.Ignored -> commit(shape(key.toString()))
                else -> apply(result)
            }
        }

        override fun onSpace() {
            if (menu.isActive) return menuSpace()
            if (ui.shift) {
                // Shift then space toggles 全形, like Shift+Space on a hardware keyboard
                ui.shift = false
                toggleFullShape()
                return
            }
            val result = if (ui.english) KeyResult.Ignored else engine.onSpace()
            if (result == KeyResult.Ignored) commit(shape(" ")) else apply(result)
        }

        override fun onBackspace() {
            if (menu.isActive) return applyMenu(menu.onBackspace())
            if (ui.english || engine.onBackspace() == KeyResult.Ignored) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            } else {
                syncUi()
            }
        }

        override fun onEnter() {
            if (menu.isActive) return applyMenu(menu.onEnter())
            if (engine.isComposing) {
                resetComposition()
            } else {
                // Performs the editor action (search, send, next...) when the field asks for one
                sendKeyChar('\n')
            }
        }

        override fun onTab() {
            if (engine.isComposing || menu.isActive) resetComposition() else sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
        }

        override fun onShift() {
            ui.shift = !ui.shift
        }

        override fun onSelect(indexOnPage: Int) =
            if (menu.isActive) applyMenu(menu.select(indexOnPage)) else apply(engine.selectOnPage(indexOnPage))

        override fun onNextPage() = if (menu.isActive) applyMenu(menu.nextPage()) else apply(engine.nextPage())

        override fun onPreviousPage() = if (menu.isActive) applyMenu(menu.previousPage()) else apply(engine.previousPage())

        override fun onToggleEnglish() {
            resetComposition()
            ui.english = !ui.english
            if (!fieldForcesEnglish) userEnglish = ui.english
        }

        override fun onToggleSettings() {
            ui.showSettings = !ui.showSettings
        }

        override fun onHide() {
            resetComposition()
            if (ui.expanded) {
                // Expanded from the hardware-keyboard toolbar: fold back to the toolbar
                ui.expanded = false
            } else {
                requestHideSelf(0)
            }
        }

        override fun onExpand() {
            ui.showSettings = false
            ui.expanded = true
        }

        override fun onLayoutChange(layout: KeyboardLayout) {
            val tableChanged = layout.tableId != ui.layout.tableId || layout.wildcard != ui.layout.wildcard
            ui.layout = layout
            layoutStore.save(layout)
            if (tableChanged) loadTable()
        }

        /** The system's keyboard picker, so switching doesn't need the notification shade. */
        override fun onSwitchIme() {
            resetComposition()
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }

        /**
         * Hands over to the system's voice input (e.g. Google 語音輸入), which has
         * its own button to come back. Andaime itself never records audio, so it
         * needs no microphone permission.
         */
        override fun onVoiceInput() {
            resetComposition()
            ui.showSettings = false
            val imm = getSystemService(InputMethodManager::class.java) ?: return
            val (voice, subtype) = findVoiceInput(imm) ?: run {
                Toast.makeText(this@AndaimeImeService, "沒有啟用的語音輸入，請在系統的輸入法設定中啟用（例如 Google 語音輸入）", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (subtype != null) switchInputMethod(voice.id, subtype) else switchInputMethod(voice.id)
            } else {
                val token = window.window?.attributes?.token ?: return
                @Suppress("DEPRECATION")
                imm.setInputMethodAndSubtype(token, voice.id, subtype)
            }
        }

        override fun onToggleSymbols() {
            resetComposition()
            ui.shift = false
            ui.symbols = !ui.symbols
        }

        override fun onLiteral(text: String) {
            resetComposition()
            commit(shape(text))
        }

        override fun onEditKey(keyCode: Int) {
            resetComposition()
            sendDownUpKeyEvents(keyCode)
        }

        override fun onToggleEmoji() {
            resetComposition()
            ui.showSettings = false
            // Checking every emoji against the font takes a moment, so only on first use
            if (ui.emojiGroups.isEmpty()) ui.emojiGroups = emojiGroups(::canDraw)
            ui.emoji = !ui.emoji
        }

        override fun onEmojiTab(tab: Int) {
            ui.emojiTab = tab
        }

        override fun onEmoji(text: String, remember: Boolean) {
            resetComposition()
            commit(text)
            if (!remember) return
            ui.recentEmoji = addRecent(ui.recentEmoji, text)
            emojiPrefs.edit().putString(RECENT_EMOJI, ui.recentEmoji.joinToString("\n")).apply()
        }

        override fun onToggleSimplified() {
            onLayoutChange(ui.layout.copy(outputSimplified = !ui.layout.outputSimplified))
        }

        override fun onOpenDictionary() {
            resetComposition()
            ui.showSettings = false
            requestHideSelf(0)
            startActivity(
                Intent(this@AndaimeImeService, DictionaryActivity::class.java)
                    .putExtra(DictionaryActivity.EXTRA_TABLE, loadedTable?.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** An enabled input method offering a "voice" subtype, or failing that one whose id says voice. */
    private fun findVoiceInput(imm: InputMethodManager): Pair<InputMethodInfo, InputMethodSubtype?>? {
        val others = imm.enabledInputMethodList.filter { it.packageName != packageName }
        for (imi in others) {
            imm.getEnabledInputMethodSubtypeList(imi, true).firstOrNull { it.mode == "voice" }?.let { return imi to it }
        }
        return others.firstOrNull { it.id.contains("voice", ignoreCase = true) }?.let { it to null }
    }

    /** Hardware / Bluetooth keyboards. Modifier chords and Shift go to the system untouched. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // `adb shell setprop log.tag.Andaime DEBUG` to see what a hardware keyboard actually sends
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "keyDown ${KeyEvent.keyCodeToString(keyCode)} scan=${event.scanCode} device=${event.device?.name}")
        }
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            shiftTap.onShiftDown(event.eventTime, event.repeatCount)
            return super.onKeyDown(keyCode, event)
        }
        shiftTap.onOtherKeyDown()

        if (keyCode == KeyEvent.KEYCODE_SPACE && ui.layout.ctrlSpaceTogglesLanguage &&
            event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
        ) {
            if (event.repeatCount == 0) keyboardActions.onToggleEnglish()
            consumedKeys += keyCode
            return true
        }
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_SPACE && event.isShiftPressed) {
            if (event.repeatCount == 0) toggleFullShape()
            consumedKeys += keyCode
            return true
        }
        if (menu.isActive) {
            consumedKeys += keyCode
            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                menuSpace()
                return true
            }
            val result = when (keyCode) {
                KeyEvent.KEYCODE_DEL -> menu.onBackspace()
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> menu.onEnter()
                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> { menu.cancel(); MenuResult.Consumed }
                // An Esc key that sends ` closes the prefix rather than typing ``
                KeyEvent.KEYCODE_GRAVE if ui.layout.graveCancels -> { menu.cancel(); MenuResult.Consumed }
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> menu.nextPage()
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> menu.previousPage()
                else -> event.getUnicodeChar(0).takeIf { it != 0 }?.let { menu.onKey(it.toChar()) } ?: MenuResult.Consumed
            }
            applyMenu(result)
            return true
        }
        if (ui.english) return typeFullShape(keyCode, event) || super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_GRAVE && !event.isShiftPressed && !engine.isComposing) {
            menu.onKey('`')
            consumedKeys += keyCode
            syncUi()
            return true
        }
        val result = when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> engine.onSpace()
            KeyEvent.KEYCODE_DEL -> engine.onBackspace()
            // Many tablet keyboards send BACK from their Esc key, so both cancel a composition
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK ->
                if (engine.isComposing) { engine.reset(); KeyResult.Consumed } else KeyResult.Ignored
            // After a lone "=" the ` key is still a symbol code (=` gives ▏▎▍)
            KeyEvent.KEYCODE_GRAVE
                if ui.layout.graveCancels && !event.isShiftPressed && engine.isComposing &&
                !(engine.isSymbolMode && engine.composition.length == 1) -> {
                engine.reset()
                KeyResult.Consumed
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> engine.nextPage()
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> engine.previousPage()
            else -> {
                if (event.isShiftPressed && engine.isSymbolMode) {
                    val c = event.getUnicodeChar(event.metaState)
                    if (c == 0) KeyResult.Consumed else engine.onKey(c.toChar())
                } else if (event.isShiftPressed) {
                    // Shift+8 is the wildcard; other shifted keys type themselves
                    if (event.getUnicodeChar(event.metaState) == DecodeRules.WILDCARD.code) {
                        engine.onKey(DecodeRules.WILDCARD)
                    } else {
                        KeyResult.Ignored
                    }
                } else {
                    val c = event.getUnicodeChar(0)
                    if (c == 0) KeyResult.Ignored else engine.onKey(c.toChar())
                }
            }
        }
        if (result == KeyResult.Ignored) {
            return (!engine.isComposing && typeFullShape(keyCode, event)) || super.onKeyDown(keyCode, event)
        }
        consumedKeys += keyCode
        apply(result)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftTap.onShiftUp(event.eventTime) && ui.layout.shiftTogglesLanguage) {
                keyboardActions.onToggleEnglish()
            }
            return super.onKeyUp(keyCode, event)
        }
        // e.g. Esc that cancelled a composition must not also close the app's dialog on release
        if (consumedKeys.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    /** In 全形 mode, types a printable hardware key as its full-width form; false if it isn't one. */
    private fun typeFullShape(keyCode: Int, event: KeyEvent): Boolean {
        if (!ui.fullShape) return false
        val c = event.getUnicodeChar(event.metaState)
        if (c !in 0x20..0x7E) return false
        commit(FullShape.convert(c.toChar()).toString())
        consumedKeys += keyCode
        return true
    }

    private fun shape(text: String) = if (ui.fullShape) FullShape.convert(text) else text

    private fun toggleFullShape() {
        ui.fullShape = !ui.fullShape
        Toast.makeText(this, if (ui.fullShape) "全形" else "半形", Toast.LENGTH_SHORT).show()
    }

    /**
     * A lone ` then Space is the table's own ` code if it has one (dayi3: 巷),
     * otherwise a plain `.
     */
    private fun menuSpace() {
        if (menu.composition != "`") return applyMenu(menu.onSpace())
        menu.cancel()
        engine.onKey('`')
        if (engine.candidates.isEmpty()) {
            engine.reset()
            commit("`")
            syncUi()
        } else {
            apply(engine.onSpace())
        }
    }

    private fun applyMenu(result: MenuResult) {
        when (result) {
            is MenuResult.Commit -> commit(result.text)
            is MenuResult.Run -> when (result.action) {
                MenuAction.SETTINGS -> ui.showSettings = true
                MenuAction.TOGGLE_SIMPLIFIED -> keyboardActions.onToggleSimplified()
                MenuAction.TOGGLE_WILDCARD -> keyboardActions.onLayoutChange(ui.layout.copy(wildcard = !ui.layout.wildcard))
                MenuAction.TOGGLE_FULL_SHAPE -> toggleFullShape()
                // Handled inside the menu
                MenuAction.UNICODE -> Unit
            }
            MenuResult.Consumed, MenuResult.Ignored -> Unit
        }
        syncUi()
    }

    /** Read from the app's assets the first time 輸出簡體 is used. */
    private val toSimplified by lazy {
        fun dictionary(name: String) = assets.open("opencc/$name").reader().let(ChineseConverter::readDictionary)
        ChineseConverter(dictionary("TSPhrases.txt"), dictionary("TSCharacters.txt"))
    }

    private fun apply(result: KeyResult) {
        if (result is KeyResult.Commit) {
            commit(if (ui.layout.outputSimplified) toSimplified.convert(result.text) else result.text)
        }
        syncUi()
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun resetComposition() {
        engine.reset()
        menu.cancel()
        syncUi()
    }

    private fun syncUi() {
        if (menu.isActive) {
            ui.composition = menu.composition
            ui.candidates = menu.pageLabels
            ui.selectionLabels = menu.selectionLabels
            ui.page = menu.page
            ui.pageCount = menu.pageCount
            return
        }
        ui.composition = engine.compositionRadicals
        ui.candidates = engine.pageCandidates
        ui.selectionLabels = engine.selectionLabels
        ui.page = engine.page
        ui.pageCount = engine.pageCount
    }
}

private const val TAG = "Andaime"
private const val RECENT_EMOJI = "recent"

/** US keyboard Shift mapping for the keys on the soft keyboard. */
private const val UNSHIFTED = "`1234567890-=[]\\;',./"
private const val SHIFTED = "~!@#$%^&*()_+{}|:\"<>?"

internal fun shifted(key: Char): Char {
    val i = UNSHIFTED.indexOf(key)
    return if (i >= 0) SHIFTED[i] else key.uppercaseChar()
}

/** Observable snapshot of what the keyboard shows. */
class KeyboardUiState {
    var composition by mutableStateOf("")
    /** Candidates on the current page only. */
    var candidates by mutableStateOf(emptyList<String>())
    var selectionLabels by mutableStateOf(emptyList<String>())
    var page by mutableStateOf(0)
    var pageCount by mutableStateOf(0)
    var keyNames by mutableStateOf(emptyMap<Char, String>())
    var tables by mutableStateOf(emptyList<InstalledTable>())
    /** Name of the table in use; null when none is installed yet. */
    var tableName by mutableStateOf<String?>(null)
    var english by mutableStateOf(false)
    /** 全形: typed ASCII becomes full-width (Ａ１！) and space becomes U+3000. */
    var fullShape by mutableStateOf(false)
    /** One-shot Shift: the next key is sent shifted (A, !, ...) and bypasses Dayi. */
    var shift by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    /** The ?123 layer (digits, symbols, editing keys) is showing instead of the letters. */
    var symbols by mutableStateOf(false)
    /** The emoji panel is showing instead of the keys. */
    var emoji by mutableStateOf(false)
    var emojiTab by mutableStateOf(RECENT_EMOJI_TAB)
    /** Filled the first time the panel opens, without emoji the font can't draw. */
    var emojiGroups by mutableStateOf(emptyList<EmojiGroup>())
    var recentEmoji by mutableStateOf(emptyList<String>())
    var layout by mutableStateOf(KeyboardLayout())
    /** A Bluetooth/USB keyboard is connected. */
    var hardwareKeyboard by mutableStateOf(false)
    /** The user opened the touch keys from the hardware-keyboard toolbar. */
    var expanded by mutableStateOf(false)

    /** Only the candidate bar and option buttons, no touch keys. */
    val toolbarOnly: Boolean
        get() = hardwareKeyboard && layout.hardwareKeyboardMode == HardwareKeyboardMode.TOOLBAR && !expanded
}
