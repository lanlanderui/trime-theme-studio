/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.kbd.ActionSlot
import com.osfans.trime.data.theme.kbd.KeyboardDraft
import com.osfans.trime.data.theme.kbd.KeyboardEditHistory
import com.osfans.trime.data.theme.kbd.LayoutEngine
import com.osfans.trime.data.theme.kbd.RawKeyboard
import com.osfans.trime.data.theme.kbd.ThemeKeyboardReader
import com.osfans.trime.data.theme.kbd.ThemeKeyboardWriter
import com.osfans.trime.ime.keyboard.Keyboard
import com.trime.editor.databinding.ActivityKeyboardEditorBinding
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.kbd.KeyboardCanvasView
import com.trime.editor.ui.preview.PreviewChrome
import com.trime.editor.ui.skin.UiMode
import com.trime.editor.ui.skin.UiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import com.trime.editor.ui.R as UiR

/**
 * One layout on a canvas, with the key under the finger described below it.
 *
 * The canvas is a real [Keyboard] built from the draft, so a key's shape, gaps
 * and caption are what the IME would draw — the editor cannot promise a layout
 * it would not produce. Edits stay in the draft until save; the file is patched
 * line by line, and only the keys that changed lose their original spelling.
 *
 * The six actions sit on the panel at all times rather than behind a "more"
 * disclosure: a key's whole meaning is its bindings, and an editor that hides
 * five of them is an editor where the sixth is never found.
 */
class KeyboardEditorActivity : EditorActivity() {
    private lateinit var binding: ActivityKeyboardEditorBinding
    private lateinit var canvas: KeyboardCanvasView
    private lateinit var chrome: PreviewChrome

    private var themeId: String = ""
    private var themeName: String = ""
    private var layoutId: String = ""
    private var layoutName: String = ""

    private var theme: Theme? = null
    private var sourceFile: File? = null
    private var raw: RawKeyboard? = null
    private var draft: KeyboardDraft? = null

    private val history = KeyboardEditHistory<KeyboardDraft.Entry>()

    private var selected: Int? = null

    /** The keyboard width the canvas is drawn at; the same number the IME gets. */
    private var canvasWidth: Int = 0

    /** Field edits land here so a drag in progress does not refill them. */
    private var bindingFields = false

    private lateinit var labelField: EditText
    private lateinit var widthField: EditText
    private lateinit var widthSlider: SeekBar
    private lateinit var widthMinus: TextView
    private lateinit var widthPlus: TextView
    private lateinit var popupValue: TextView
    private lateinit var actionFields: List<Pair<ActionSlot, EditText>>
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyboardEditorBinding.inflate(layoutInflater)
        // 舞台皮肤没有顶栏：返回/保存在舞台的胶囊上（设计稿 B3-02）。
        if (UiMode.caps.stageHeader) {
            binding.toolbar.visibility = View.GONE
            binding.stageChips.visibility = View.VISIBLE
            binding.stageBack.setOnClickListener { finish() }
            binding.stageSave.setOnClickListener { save() }
        }
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        layoutId = intent.getStringExtra(EXTRA_LAYOUT_ID).orEmpty()
        layoutName = intent.getStringExtra(EXTRA_LAYOUT_NAME).orEmpty()
        if (themeId.isEmpty() || layoutId.isEmpty()) {
            finish()
            return
        }
        supportActionBar?.title = themeName.ifEmpty { getString(R.string.app_name) }
        supportActionBar?.subtitle = getString(R.string.kbd_editor_subtitle, layoutName.ifEmpty { layoutId }, layoutId)

        canvas = KeyboardCanvasView(this)
        // 画布外面包一层「整块输入区」：上面候选栏、下面 keyboard_padding_bottom。
        // 只画键盘会让人以为键盘贴着屏幕底边。
        chrome = PreviewChrome(this).also { it.wrap(canvas) }
        binding.canvasHost.addView(
            chrome,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        // N-5：舞台显示「完整输入区」还是「只画键盘」。默认完整（所见即所得），
        // 关掉就是设计稿 B3 那种干净舞台；键盘宽度不变，D2 像素闸门不受影响。
        binding.stageBands.setOnClickListener {
            EditorPrefs.stageBandsVisible = !EditorPrefs.stageBandsVisible
            applyStageBands()
        }
        applyStageBands()
        canvas.onSelect = ::select
        canvas.onWidthPreview = { index, weight -> previewWidth(index, weight) }
        canvas.onWidthCommit = { index, weight -> commitWidth(index, weight) }
        buildInspector()
        binding.addButton.setOnClickListener(::addKey)
        binding.deleteButton.setOnClickListener(::deleteKey)
        binding.leftButton.setOnClickListener(::moveLeft)
        binding.rightButton.setOnClickListener(::moveRight)
        binding.undoButton.setOnClickListener(::undo)
        binding.redoButton.setOnClickListener(::redo)
        binding.toolbar.post { canvasWidth = binding.canvasHost.width }
        load()
    }

    /** N-5：切带子可见性；N-6：青色小点只在「仅键盘」模式亮起。 */
    private fun applyStageBands() {
        val visible = EditorPrefs.stageBandsVisible
        chrome.setBandsVisible(visible)
        binding.stageBands.text =
            getString(if (visible) R.string.stage_bands_full else R.string.stage_bands_keys)
        binding.stageHighlight.alpha = if (visible) 0.3f else 1f
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.keyboard_editor, menu)
        OutlineMenu.addTo(menu, this)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_undo)?.isEnabled = history.canUndo
        menu.findItem(R.id.action_redo)?.isEnabled = history.canRedo
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (OutlineMenu.handle(this, item, themeId, themeName)) return true
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_undo -> {
                undo(binding.toolbar)
                return true
            }

            R.id.action_redo -> {
                redo(binding.toolbar)
                return true
            }

            R.id.action_save -> {
                save()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------- loading

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { read() }
            if (isFinishing || isDestroyed) return@launch
            if (loaded == null) {
                toast(R.string.kbd_load_failed)
                finish()
                return@launch
            }
            theme = loaded.first
            // 候选栏是按「当前挂载的主题」取色的（ColorManager），不挂上就会拿
            // 上一次别的主题的颜色来画——预览就成了另一个主题的样子。
            ColorManager.attachTheme(loaded.first)
            sourceFile = loaded.second
            afterReload(select = true)
        }
    }

    /** The theme, and the file the writer patches — the same file, decoded. */
    private fun read(): Pair<Theme, File>? {
        val file = ThemeStyleWriter.editableSourceFile(themeId) ?: return null
        val result = ThemeLoader.loadFromSource(themeId, file)
        val theme = (result as? ThemeLoader.ThemeLoadResult.Success)?.theme ?: return null
        return theme to file
    }

    /**
     * Re-reads the layout block and rebuilds the draft around it.
     *
     * Runs after every save: the writer reports the lines it patched, and a
     * draft still holding yesterday's line numbers would refuse the next save
     * as "the file changed".
     */
    private fun afterReload(select: Boolean) {
        val file = sourceFile ?: return
        val block = ThemeKeyboardReader.read(file).firstOrNull { it.id == layoutId }
        if (block == null) {
            toast(R.string.kbd_load_failed)
            finish()
            return
        }
        val config = theme?.presetKeyboards?.get(layoutId)
        if (config == null || block.keys.isEmpty()) {
            // An alias: it borrows its keys, so there is nothing here to patch.
            AlertDialog.Builder(this)
                .setTitle(R.string.kbd_cannot_edit_title)
                .setMessage(getString(R.string.kbd_badge_alias, block.includeTarget.orEmpty()))
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .show()
            return
        }
        raw = block
        // popup 由读取器宽松解析后单独喂进来：模型只认列表写法，`popup: 'q w'`
        // 会解成空，照着模型编辑等于把那行删掉。读不懂的形状按「没有」处理，
        // 写入器认得出那种情况，不会去动它。
        draft = KeyboardDraft(config, block.keys.map { ThemeKeyboardReader.popupOf(it).orEmpty() })
        history.clear()
        if (select || selected == null) selected = 0
        canvasWidth = binding.canvasHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        redraw()
        invalidateOptionsMenu()
    }

    // -------------------------------------------------------------- rendering

    private fun redraw() {
        val t = theme ?: return
        val d = draft ?: return
        val config = d.toConfig()
        chrome.applyStyle(t)
        canvas.applyThemeMetrics(t)
        val keyboard = Keyboard(this, t, canvasWidth, config)
        canvas.keyboard = keyboard

        val rows = LayoutEngine.rows(config, canvasWidth)
        val weights = FloatArray(d.size) { d.effectiveWidth(it) }
        canvas.state = KeyboardCanvasView.State(weights, FloatArray(rows.size) { rows[it].weightSum })
        val map = d.indexMap(keyboard.keys.size)
        canvas.draftToKey = map.draftToKey
        canvas.keyToDraft = map.keyToDraft
        canvas.blanks = blanksOf(rows, d, keyboard)
        canvas.selectedIndex = selected
        fillInspector()
        updateStatus()
    }

    /**
     * Where the action-less keys sit.
     *
     * They build no `Key`, so their rectangle is taken from the two real keys on
     * either side: the gap between them is exactly the width the spacer claimed.
     * Laying it out from the weights instead would drift from the keyboard the
     * moment a row is normalised to the full width.
     */
    private fun blanksOf(
        rows: List<LayoutEngine.Row>,
        d: KeyboardDraft,
        keyboard: Keyboard,
    ): List<KeyboardCanvasView.BlankMark> {
        val out = mutableListOf<KeyboardCanvasView.BlankMark>()
        rows.forEachIndexed { rowIndex, row ->
            // The row's real keys, in draft order: the n-th producing key of the
            // row is `keysInRow[n]`, so the keys on either side of a spacer run
            // are exactly the ones the IME draws.
            val keysInRow = keyboard.keys.filter { it.row == rowIndex }
            var produced = 0
            var runStart = -1
            row.indices.forEachIndexed { position, index ->
                if (d.producesKey(index)) {
                    if (runStart >= 0) {
                        out += blankBox(keysInRow, produced, row.indices[runStart], runStart, position - 1)
                        runStart = -1
                    }
                    produced++
                } else if (runStart < 0) {
                    runStart = position
                }
            }
            if (runStart >= 0) out += blankBox(keysInRow, produced, row.indices[runStart], runStart, row.indices.lastIndex)
        }
        return out
    }

    /**
     * One dashed box spanning a spacer run.
     *
     * [produced] is how many real keys the row had emitted before the run: the
     * gap therefore starts at the previous key's right edge and ends at the next
     * one's left edge. Both edges come from the keyboard itself — computing them
     * from weights would drift as soon as a row is normalised to full width.
     */
    private fun blankBox(
        keysInRow: List<com.osfans.trime.ime.keyboard.Key>,
        produced: Int,
        draftIndex: Int,
        @Suppress("UNUSED_PARAMETER") from: Int,
        @Suppress("UNUSED_PARAMETER") to: Int,
    ): KeyboardCanvasView.BlankMark {
        val left = keysInRow.getOrNull(produced - 1)?.let { (it.x + it.width).toFloat() } ?: 0f
        val right = keysInRow.getOrNull(produced)?.x?.toFloat() ?: canvasWidth.toFloat()
        val top = keysInRow.firstOrNull()?.y?.toFloat() ?: 0f
        val bottom = keysInRow.firstOrNull()?.let { (it.y + it.height).toFloat() } ?: top
        return KeyboardCanvasView.BlankMark(draftIndex, android.graphics.RectF(left, top, right, bottom))
    }

    private fun updateStatus() {
        val d = draft ?: return
        val spacers = (0 until d.size).count { !d.producesKey(it) }
        statusText.text = getString(R.string.kbd_editor_status, d.size, spacers, selected?.plus(1) ?: 0)
        // dock 是自绘的胶囊项，没有 Button 自带的禁用态：用透明度表达「按不了」。
        dockState(binding.undoButton, history.canUndo)
        dockState(binding.redoButton, history.canRedo)
        dockState(binding.deleteButton, d.size > 1 && selected != null)
        dockState(binding.leftButton, selected != null)
        dockState(binding.rightButton, selected != null)
    }

    private fun dockState(
        item: View,
        enabled: Boolean,
    ) {
        item.isEnabled = enabled
        item.alpha = if (enabled) 1f else 0.38f
    }

    // -------------------------------------------------------------- inspector

    /**
     * 滚动区里的三档卡片（设计稿 B3-05/06/07）。
     *
     * 一屏只描述「当前选中的那个键」：大卡给文字，宽度卡给滑杆，半卡给六个
     * 动作槽。三档的圆角不同（26 / 22 / 卡片 28）是稿子的语言——层级靠形状
     * 拉开，不靠描边和阴影。
     */
    private fun buildInspector() {
        val scroll = ScrollView(this)
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                // B3-09：dock 高 56 + 距底 16，内容尾部留 96，
                // 滚到底最后一张卡也露得全。
                setPadding(dp(14), dp(4), dp(14), dp(96))
            }
        scroll.addView(column)
        binding.inspectorHost.addView(
            scroll,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        // B3-04 的键数读数在舞台上（布局里的 stageStatus），不跟着滚动走。
        statusText = binding.stageStatus

        labelField =
            addBigField(column, getString(R.string.kbd_field_label)) { value ->
                mutate(coalesce = "label") { d, index ->
                    // 没钉过 label 的键，卡上显示的是 click 的名字（键面上画的也是它）。
                    // 照着它原样提交等于把「兜底」钉成字面量——文件里会多一行 label，
                    // 而用户什么都没改。所以原样提交算没改。
                    val pinned = d.key(index).label
                    if (pinned.isEmpty() && value == d.action(index, ActionSlot.CLICK).orEmpty()) return@mutate
                    d.setLabel(index, value)
                }
            }
        addWidthCard(column)

        // 六个动作槽两两一张半卡：稿子画了单击/长按两张，后四个滑动槽沿用同一
        // 档卡片语言，功能一个不少（见 addHalfCards 的注释）。
        val fields = mutableListOf<Pair<ActionSlot, EditText>>()
        ActionSlot.entries.filter { it.isPrimary }.chunked(2).forEach { pair ->
            fields +=
                addHalfCards(column, pair) { slot, value ->
                    mutate(coalesce = "action-${slot.yaml}") { d, index ->
                        d.setAction(index, slot, value.ifBlank { null })
                    }
                }
        }
        actionFields = fields
        addPopupCard(column)
    }

    /** 卡片之间统一的上边距：一屏的卡片是「叠起来的块」，间距靠它。 */
    private fun topMargin(value: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = value }

    /** 一张 B3 卡片：底色 + 一档圆角 + 1dp 描边，里面摆什么由调用方决定。 */
    private fun cardView(@AttrRes radiusAttr: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = UiTokens.rounded(this@KeyboardEditorActivity, radiusAttr)
    }

    /** 字段标签：小字、无衬线 medium、宽字距（稿子的 .lb，muted 色）。 */
    private fun fieldTitle(
        text: String,
        sizeSp: Float = 9f,
        spacing: Float = 0.08f,
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        letterSpacing = spacing
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(UiTokens.color(this@KeyboardEditorActivity, UiR.attr.ui_muted))
    }

    /**
     * B3-05 文字大卡：标题 + 30sp 的值 + 右上 44dp 主色圆钮（✎）。
     *
     * ✎ 不是装饰：它是「专心改这一项」的入口——点它聚焦输入框并抬起软键盘，
     * 免得用户在 30sp 的大字上找光标。值直接摆在卡片上（不要输入框的底），
     * 卡片就是它的底。
     */
    private fun addBigField(
        parent: LinearLayout,
        title: String,
        onCommit: (String) -> Unit,
    ): EditText {
        val field =
            EditText(this).apply {
                background = null
                setTextSize(30f)
                letterSpacing = -0.02f
                // N-4：稿子三屏的大字都是 640/680 字重；Android 没有可变字重，
                // 700 是最近档。B1/B2 标题已用 bold，这里跟上，三屏一致。
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(UiTokens.color(this@KeyboardEditorActivity, UiR.attr.ui_ink))
                setPadding(0, dp(4), 0, 0)
                setSingleLine(true)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { view, _, _ ->
                    commitField(view as EditText, onCommit)
                    true
                }
                setOnFocusChangeListener { view, hasFocus ->
                    if (!hasFocus && view is EditText) commitField(view, onCommit)
                }
            }
        val head =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        head.addView(
            fieldTitle(title, sizeSp = 10f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        head.addView(
            roundButton(
                glyph = "✎",
                description = getString(R.string.kbd_field_label),
                glyphSp = 17f,
                fillAttr = UiR.attr.ui_primary_container,
                textAttr = UiR.attr.ui_on_primary_container,
            ) {
                field.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(field, 0)
            },
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        val card =
            cardView(UiR.attr.ui_radius_field).apply {
                layoutParams = topMargin(dp(10))
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
        card.addView(head)
        card.addView(field)
        parent.addView(card)
        return field
    }

    /**
     * B3-06 宽度卡：标题一行，下面 − 圆钮 / 17sp 数字 + 4dp 滑杆 / ＋ 圆钮。
     *
     * 滑杆是自绘的 4dp 轨道（bg_slider_track）：默认样式是一条粗棒，和这屏的
     * 细字阶不搭。拖动只预览不记历史，松手才记一条（见 preview/commitWidth）。
     */
    private fun addWidthCard(parent: LinearLayout) {
        val card =
            cardView(UiR.attr.ui_radius_field).apply {
                layoutParams = topMargin(dp(10))
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
        card.addView(fieldTitle(getString(R.string.kbd_field_width), sizeSp = 9f, spacing = 0.1f))

        val commit: (String) -> Unit = { value ->
            value.toFloatOrNull()?.let { weight ->
                mutate(coalesce = "width") { d, index -> d.setWidth(index, weight) }
            }
        }
        widthField =
            EditText(this).apply {
                background = null
                setTextSize(17f)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(UiTokens.color(this@KeyboardEditorActivity, UiR.attr.ui_ink))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                setSingleLine(true)
                setOnEditorActionListener { view, _, _ ->
                    commitField(view as EditText, commit)
                    true
                }
                setOnFocusChangeListener { view, hasFocus ->
                    if (!hasFocus && view is EditText) commitField(view, commit)
                }
            }
        widthSlider =
            SeekBar(this).apply {
                progressDrawable = ContextCompat.getDrawable(context, UiR.drawable.bg_slider_track)
                thumb = ContextCompat.getDrawable(context, UiR.drawable.bg_slider_thumb)
                splitTrack = false
                minimumHeight = 0
                max = WIDTH_SLIDER_SPAN
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            bar: SeekBar,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            // 程序化设置（fillInspector 同步）也会进来，只认手指。
                            if (!fromUser) return
                            val index = selected ?: return
                            previewWidth(index, progress + WIDTH_MIN.toFloat())
                        }

                        override fun onStartTrackingTouch(bar: SeekBar) = Unit

                        override fun onStopTrackingTouch(bar: SeekBar) {
                            val index = selected ?: return
                            commitWidth(index, bar.progress + WIDTH_MIN.toFloat())
                        }
                    },
                )
            }
        widthMinus =
            roundButton(
                glyph = "−",
                description = getString(R.string.kbd_width_decrease),
                glyphSp = 18f,
                fillAttr = UiR.attr.ui_card_high,
                textAttr = UiR.attr.ui_ink,
            ) { nudgeWidth(-WIDTH_STEP) }
        widthPlus =
            roundButton(
                glyph = "＋",
                description = getString(R.string.kbd_width_increase),
                glyphSp = 18f,
                fillAttr = UiR.attr.ui_card_high,
                textAttr = UiR.attr.ui_ink,
            ) { nudgeWidth(WIDTH_STEP) }

        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
        row.addView(widthMinus, LinearLayout.LayoutParams(dp(36), dp(36)))
        val mid =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
        mid.addView(
            widthField,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        mid.addView(
            widthSlider,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) },
        )
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(widthPlus, LinearLayout.LayoutParams(dp(36), dp(36)))
        card.addView(row)
        parent.addView(card)
    }

    /**
     * 圆钮：✎ 用主色容器底，−/＋ 用卡片色底——稿子里两种圆钮的底色不同
     * （pcontainer / scontainer），文字色跟着底色走，都是整圆。
     *
     * [description] 给读屏用：字形本身（−、✎）念不出来是什么。
     */
    private fun roundButton(
        glyph: String,
        description: String,
        glyphSp: Float,
        @AttrRes fillAttr: Int,
        @AttrRes textAttr: Int,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = glyph
        textSize = glyphSp
        gravity = Gravity.CENTER
        contentDescription = description
        setTextColor(UiTokens.color(this@KeyboardEditorActivity, textAttr))
        background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(UiTokens.color(this@KeyboardEditorActivity, fillAttr))
            }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /**
     * B3-07 半卡：两张并排（单击/长按、上滑/下滑、左滑/右滑）。
     *
     * 稿子只画了前两张——编辑器的六个动作槽都要能改，后四个滑动槽沿用同一档
     * 卡片（圆角 22、标题 9sp、值 15sp/600 等宽），不引入第三种字段样式。
     */
    private fun addHalfCards(
        parent: LinearLayout,
        slots: List<ActionSlot>,
        onCommit: (ActionSlot, String) -> Unit,
    ): List<Pair<ActionSlot, EditText>> {
        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = topMargin(dp(10))
            }
        val out = mutableListOf<Pair<ActionSlot, EditText>>()
        slots.forEachIndexed { i, slot ->
            val field =
                EditText(this).apply {
                    background = null
                    setTextSize(15f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(UiTokens.color(this@KeyboardEditorActivity, UiR.attr.ui_ink))
                    setPadding(0, dp(4), 0, 0)
                    setSingleLine(true)
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    setOnEditorActionListener { view, _, _ ->
                        commitField(view as EditText) { value -> onCommit(slot, value) }
                        true
                    }
                    setOnFocusChangeListener { view, hasFocus ->
                        if (!hasFocus && view is EditText) commitField(view) { value -> onCommit(slot, value) }
                    }
                }
            val card =
                cardView(UiR.attr.ui_radius_half).apply {
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
            card.addView(fieldTitle(getString(slot.titleRes())))
            card.addView(field)
            row.addView(
                card,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    // 两张之间留 10dp：一边 5dp，比在中间塞 Space 省一次测量。
                    if (i == 0) marginEnd = dp(5) else marginStart = dp(5)
                },
            )
            out += slot to field
        }
        parent.addView(row)
        return out
    }

    private fun commitField(
        field: EditText,
        onCommit: (String) -> Unit,
    ) {
        if (bindingFields) return
        onCommit(field.text.toString().trim())
    }

    private fun stepButton(
        label: String,
        description: String,
        onClick: () -> Unit,
    ): Button = Button(this).apply {
        text = label
        contentDescription = description
        textSize = 20f
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        setOnClickListener { onClick() }
    }

    /**
     * 长按弹出（popup）：一张满宽卡，点值打开逐项编辑的对话框。
     *
     * 不用输入框：token 里可能有逗号或空格（`，`、`space`、`Return`），
     * 靠分隔符切迟早切错（理由见 editPopup）。
     */
    private fun addPopupCard(parent: LinearLayout) {
        val card =
            cardView(UiR.attr.ui_radius_field).apply {
                layoutParams = topMargin(dp(10))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { editPopup() }
            }
        card.addView(fieldTitle(getString(R.string.kbd_field_popup)))
        popupValue =
            TextView(this).apply {
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(UiTokens.color(this@KeyboardEditorActivity, UiR.attr.ui_ink))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
                setOnClickListener { editPopup() }
            }
        card.addView(popupValue)
        parent.addView(card)
    }

    /** 逐项编辑 `popup`：一行一个 token，可以加、可以删。 */
    private fun editPopup() {
        val d = draft ?: return
        val index = selected ?: return
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(4), dp(20), 0)
            }
        val rows = mutableListOf<Pair<LinearLayout, EditText>>()

        fun addRow(value: String) {
            val row =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            val edit =
                EditText(this).apply {
                    setText(value)
                    setSingleLine(true)
                    textSize = 16f
                    setPadding(0, dp(6), 0, dp(6))
                }
            val remove =
                stepButton("×", getString(R.string.kbd_popup_remove)) {
                    column.removeView(row)
                    rows.removeAll { it.first === row }
                }
            row.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(remove, LinearLayout.LayoutParams(dp(48), dp(44)))
            column.addView(row)
            rows += row to edit
        }

        d.popup(index).forEach(::addRow)
        column.addView(
            Button(this).apply {
                setText(R.string.kbd_popup_add)
                setOnClickListener { addRow("") }
            },
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.kbd_field_popup)
            .setMessage(R.string.kbd_popup_hint)
            .setView(ScrollView(this).apply { addView(column) })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val items = rows.map { it.second.text.toString().trim() }.filter { it.isNotEmpty() }
                // mutate 自己会比较快照；没变就不进撤销栈。
                mutate(coalesce = "popup") { draft, i -> draft.setPopup(i, items) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** 加减键：一次一格，撞到 1 或当前量程上限就停手（不改文件、不进撤销栈）。 */
    private fun nudgeWidth(delta: Int) {
        mutate(coalesce = "width") { draft, index ->
            val upper = (widthSlider.max + WIDTH_MIN).toFloat()
            val next = (draft.effectiveWidth(index) + delta).coerceIn(WIDTH_MIN.toFloat(), upper)
            draft.setWidth(index, next)
        }
    }

    /**
     * 把滑杆摆到 [weight] 上。
     *
     * 权重超过 100 时把量程撑到它，否则滑块贴在右端、再也调不回去——自定义
     * 主题里确实有把一行写成 120 的。
     */
    private fun syncWidthSlider(weight: Float) {
        val span = (weight.roundToInt() - WIDTH_MIN).coerceAtLeast(WIDTH_SLIDER_SPAN)
        if (widthSlider.max != span) widthSlider.max = span
        widthSlider.progress = (weight.roundToInt() - WIDTH_MIN).coerceIn(0, span)
    }

    private fun fillInspector() {
        bindingFields = true
        val d = draft
        val index = selected
        val enabled = d != null && index != null
        labelField.isEnabled = enabled
        widthField.isEnabled = enabled
        widthSlider.isEnabled = enabled
        widthMinus.isEnabled = enabled
        widthPlus.isEnabled = enabled
        popupValue.isEnabled = enabled
        actionFields.forEach { (_, field) -> field.isEnabled = enabled }
        if (d != null && index != null) {
            // 大卡显示「键面上画的字」：钉了 label 就显示 label，没钉就显示 click
            // 的名字（兜底值）。commit 那一侧有守卫，照兜底值原样提交不算改。
            labelField.setText(d.key(index).label.ifEmpty { d.action(index, ActionSlot.CLICK).orEmpty() })
            val weight = d.effectiveWidth(index)
            widthField.setText(formatWeight(weight))
            syncWidthSlider(weight)
            popupValue.text =
                d.popup(index).takeIf { it.isNotEmpty() }?.joinToString("、")
                    ?: getString(R.string.kbd_popup_empty)
            actionFields.forEach { (slot, field) -> field.setText(d.action(index, slot).orEmpty()) }
        } else {
            labelField.setText("")
            widthField.setText("")
            widthSlider.progress = 0
            popupValue.text = ""
            actionFields.forEach { (_, field) -> field.setText("") }
        }
        bindingFields = false
    }

    private fun formatWeight(value: Float): String = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

    // ------------------------------------------------------------------ edits

    /** Applies [block] to the selected key, with undo and a redraw. */
    private fun mutate(
        coalesce: String,
        block: (KeyboardDraft, Int) -> Unit,
    ) {
        val d = draft ?: return
        val index = selected ?: return
        val before = d.snapshot()
        block(d, index)
        if (d.snapshot() == before) return
        history.record(before, coalesce, System.currentTimeMillis())
        redraw()
        invalidateOptionsMenu()
    }

    /**
     * 拖动开始前的那一份快照。
     *
     * 拖动过程不记历史（每个像素一条会把撤销栈冲掉），但松手时要记的「改之前」
     * 是**动手之前**的状态。`previewWidth` 已经把 draft 改成终值了，若在
     * `commitWidth` 里再取当前值当 before，比较结果永远是「没变」——撤销栈里
     * 就少了这一步，一次撤销会连带把上一次加减键的改动一起吃掉。
     */
    private var widthOrigin: List<KeyboardDraft.Entry>? = null

    private fun previewWidth(
        index: Int,
        weight: Float,
    ) {
        val d = draft ?: return
        // Live drag: no history entry per pixel, only the final commit records one.
        if (widthOrigin == null) widthOrigin = d.snapshot()
        d.setWidth(index, weight)
        redraw()
    }

    private fun commitWidth(
        index: Int,
        weight: Float,
    ) {
        val d = draft ?: return
        val origin = widthOrigin
        widthOrigin = null
        d.setWidth(index, weight)
        val after = d.snapshot()
        if (origin != null && origin != after) history.record(origin, null, System.currentTimeMillis())
        redraw()
        invalidateOptionsMenu()
    }

    private fun select(index: Int?) {
        // 换键等于放弃上一次没提交的拖动起点，否则下一次松手会记到别人头上。
        widthOrigin = null
        selected = index
        canvas.selectedIndex = index
        fillInspector()
        updateStatus()
    }

    fun undo(view: View) {
        val d = draft ?: return
        val restored = history.undo(d.snapshot()) ?: return
        widthOrigin = null
        d.restore(restored)
        redraw()
        invalidateOptionsMenu()
    }

    fun redo(view: View) {
        val d = draft ?: return
        val restored = history.redo(d.snapshot()) ?: return
        widthOrigin = null
        d.restore(restored)
        redraw()
        invalidateOptionsMenu()
    }

    // --------------------------------------------------------------- actions

    fun addKey(view: View) {
        val d = draft ?: return
        val before = d.snapshot()
        val at = d.insertBlank(selected ?: (d.size - 1))
        history.record(before, null, System.currentTimeMillis())
        selected = at
        redraw()
        invalidateOptionsMenu()
    }

    fun deleteKey(view: View) {
        val d = draft ?: return
        val index = selected ?: return
        val before = d.snapshot()
        if (!d.delete(index)) {
            toast(R.string.kbd_last_key)
            return
        }
        history.record(before, null, System.currentTimeMillis())
        selected = index.coerceAtMost(d.size - 1)
        redraw()
        invalidateOptionsMenu()
    }

    fun moveLeft(view: View) = move(-1)

    fun moveRight(view: View) = move(1)

    private fun move(delta: Int) {
        val d = draft ?: return
        val index = selected ?: return
        val before = d.snapshot()
        val to = d.move(index, delta) ?: return
        history.record(before, null, System.currentTimeMillis())
        selected = to
        redraw()
        invalidateOptionsMenu()
    }

    private fun save() {
        val d = draft ?: return
        val file = sourceFile ?: return
        val block = raw ?: return
        if (d.size == 0) {
            // R17: a layout with no keys is not a layout. Refuse before writing
            // rather than leave a block the theme decoder will choke on.
            AlertDialog.Builder(this)
                .setTitle(R.string.kbd_save_failed)
                .setMessage(R.string.kbd_empty_keys)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val keys = d.toConfig().keys
        val origins = d.origins()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ThemeKeyboardWriter.replaceKeys(file, block, keys, origins) }
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is ThemeKeyboardWriter.Result.Success -> {
                    toast(R.string.kbd_saved)
                    // Line numbers moved: re-read before the next save.
                    afterReload(select = false)
                }

                is ThemeKeyboardWriter.Result.Failure ->
                    AlertDialog.Builder(this@KeyboardEditorActivity)
                        .setTitle(R.string.kbd_save_failed)
                        .setMessage(result.reason)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        /** 滑杆刻度 1…100：1 是「细到不能再细」，100 是一整行。 */
        private const val WIDTH_MIN = 1
        private const val WIDTH_SLIDER_MAX = 100
        private const val WIDTH_SLIDER_SPAN = WIDTH_SLIDER_MAX - WIDTH_MIN

        /** 加减键一次走一格。 */
        private const val WIDTH_STEP = 1

        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"
        private const val EXTRA_LAYOUT_ID = "layout_id"
        private const val EXTRA_LAYOUT_NAME = "layout_name"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
            layoutId: String,
            layoutName: String,
        ): Intent = Intent(context, KeyboardEditorActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
            .putExtra(EXTRA_LAYOUT_ID, layoutId)
            .putExtra(EXTRA_LAYOUT_NAME, layoutName)
    }
}

/** Title of an action slot on the panel. */
private fun ActionSlot.titleRes(): Int = when (this) {
    ActionSlot.CLICK -> R.string.kbd_action_click
    ActionSlot.LONG_CLICK -> R.string.kbd_action_long_click
    ActionSlot.SWIPE_UP -> R.string.kbd_action_swipe_up
    ActionSlot.SWIPE_DOWN -> R.string.kbd_action_swipe_down
    ActionSlot.SWIPE_LEFT -> R.string.kbd_action_swipe_left
    ActionSlot.SWIPE_RIGHT -> R.string.kbd_action_swipe_right
    else -> R.string.kbd_action_advanced
}
