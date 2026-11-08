/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent

/**
 * librime 的键名表。
 *
 * 输入法里这张表由 KSP 在 `:core` 生成（`RimeKeyMapping`，一千多行键名到键码的常量），
 * 编辑器没有 librime 也就没有它。所以这里只定形状：
 *
 * - 输入法启动时把生成的那张表装进 [rimeKeyTable]，行为与搬出来之前逐字一致；
 * - 编辑器用 [BasicRimeKeys]——主题里真正会出现的名字（字母、数字、标点、
 *   `Return`/`BackSpace` 这类）都覆盖到了，冷门键名会退化成「认不出」。
 *
 * 退化只影响诊断文案和未标 label 的键显示名（`preset.label` 优先），
 * 不会让键点不动——认不出的 send 在输入法里同样会退化成惰性键。
 */
interface RimeKeyTable {
    /** `q`..`p` 之类的小写名字 → 合成的大写键码（[UPPER_CODE_OFFSET] 偏移）。 */
    fun upperNameToCode(name: String): Int?

    /** `comma`/`numbersign` 之类的标点名字 → 合成的符号键码（[SYMBOL_CODE_OFFSET] 偏移）。 */
    fun symbolNameToCode(name: String): Int?

    /** 单个可见字符 → 键码。 */
    fun charToCode(name: String): Int?

    /** rime 键名 → Android 键码，认不出返回 [KeyEvent.KEYCODE_UNKNOWN]。 */
    fun nameToKeyCode(name: String): Int

    fun upperCodeToName(code: Int): String?

    fun symbolCodeToName(code: Int): String?

    fun keyCodeToName(code: Int): String?

    /** 合成符号键码 → 该符号的显示字（`comma` → `,`）。 */
    fun symbolCodeToLabel(code: Int): String?

    companion object {
        /** 合成符号键码的起点，与 librime 的约定一致。 */
        const val SYMBOL_CODE_OFFSET = 10000

        /** 合成大写键码的起点。 */
        const val UPPER_CODE_OFFSET = 20000
    }
}

/**
 * 内置的键名子集。
 *
 * 名字取自 rime 的键名表，键码写 [KeyEvent] 的常量——**不调
 * `KeyEvent.keyCodeFromString`**：那是平台方法，JVM 单测里一调就抛
 * （`Method ... not mocked`），而这个对象会在类初始化时建表。
 *
 * 认不出的名字最后交给 [KeyCode.androidKeyNameToCode]，与搬出来之前是同一条路径，
 * 于是单测里把它 stub 成 `{ 0 }` 仍然能拦住「平台键名解析成功」的分支。
 */
private object BasicRimeKeys : RimeKeyTable {
    /** rime 键名 → 键码。只收主题里会写的，收全了也没人用。 */
    private val names: Map<String, Int> = mapOf(
        "space" to KeyEvent.KEYCODE_SPACE,
        "Tab" to KeyEvent.KEYCODE_TAB,
        "Escape" to KeyEvent.KEYCODE_ESCAPE,
        "BackSpace" to KeyEvent.KEYCODE_DEL,
        "Delete" to KeyEvent.KEYCODE_FORWARD_DEL,
        "Return" to KeyEvent.KEYCODE_ENTER,
        "KP_Enter" to KeyEvent.KEYCODE_NUMPAD_ENTER,
        "Insert" to KeyEvent.KEYCODE_INSERT,
        "Home" to KeyEvent.KEYCODE_MOVE_HOME,
        "End" to KeyEvent.KEYCODE_MOVE_END,
        "Page_Up" to KeyEvent.KEYCODE_PAGE_UP,
        "Page_Down" to KeyEvent.KEYCODE_PAGE_DOWN,
        "Up" to KeyEvent.KEYCODE_DPAD_UP,
        "Down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "Left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "Right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "Caps_Lock" to KeyEvent.KEYCODE_CAPS_LOCK,
        "Num_Lock" to KeyEvent.KEYCODE_NUM_LOCK,
        "Scroll_Lock" to KeyEvent.KEYCODE_SCROLL_LOCK,
        "Print" to KeyEvent.KEYCODE_SYSRQ,
        "Pause" to KeyEvent.KEYCODE_BREAK,
        "Menu" to KeyEvent.KEYCODE_MENU,
        "Help" to KeyEvent.KEYCODE_HELP,
        "Shift_L" to KeyEvent.KEYCODE_SHIFT_LEFT,
        "Shift_R" to KeyEvent.KEYCODE_SHIFT_RIGHT,
        "Control_L" to KeyEvent.KEYCODE_CTRL_LEFT,
        "Control_R" to KeyEvent.KEYCODE_CTRL_RIGHT,
        "Alt_L" to KeyEvent.KEYCODE_ALT_LEFT,
        "Alt_R" to KeyEvent.KEYCODE_ALT_RIGHT,
        "Super_L" to KeyEvent.KEYCODE_META_LEFT,
        "Super_R" to KeyEvent.KEYCODE_META_RIGHT,
        "Multi_key" to KeyEvent.KEYCODE_SYM,
        // trime 把 Eisu_toggle 当「切键盘」键用（`CommonKeyboardActionListener` 截获后走
        // `switchKeyboard(select)`），主题里满篇都是它。编辑器这张表原来没收，于是静态
        // 检查把每个用它的动作都报成「认不出 send」——报的是编辑器的盲区，不是主题的错。
        "Eisu_toggle" to KeyEvent.KEYCODE_EISU,

        // 输入法把 `!` `?` `^` 这些「一个符号的 X11 名字」收在符号表里，落到合成键码
        // 10000+（见 codegen 的 SYMBOL_CODE_OFFSET）。主题里 `send: asciicircum` 是能用的，
        // 编辑器这张表原来没有，于是又变成一片「认不出 send」。编号与输入法保持一致，
        // 免得同一个名字在两处指着不同的东西。
        "exclam" to 10000,
        "quotedbl" to 10001,
        "dollar" to 10002,
        "percent" to 10003,
        "ampersand" to 10004,
        "colon" to 10005,
        "less" to 10006,
        "greater" to 10007,
        "question" to 10008,
        "asciicircum" to 10009,
        "underscore" to 10010,
        "braceleft" to 10011,
        "bar" to 10012,
        "braceright" to 10013,
        "asciitilde" to 10014,
        "equal" to KeyEvent.KEYCODE_EQUALS,
        "bracketleft" to KeyEvent.KEYCODE_LEFT_BRACKET,
        "bracketright" to KeyEvent.KEYCODE_RIGHT_BRACKET,
        "numbersign" to KeyEvent.KEYCODE_POUND,
        "asterisk" to KeyEvent.KEYCODE_STAR,
        "plus" to KeyEvent.KEYCODE_PLUS,
        "minus" to KeyEvent.KEYCODE_MINUS,
        "grave" to KeyEvent.KEYCODE_GRAVE,
        "comma" to KeyEvent.KEYCODE_COMMA,
        "period" to KeyEvent.KEYCODE_PERIOD,
        "slash" to KeyEvent.KEYCODE_SLASH,
        "backslash" to KeyEvent.KEYCODE_BACKSLASH,
        "semicolon" to KeyEvent.KEYCODE_SEMICOLON,
        "apostrophe" to KeyEvent.KEYCODE_APOSTROPHE,
        "at" to KeyEvent.KEYCODE_AT,
        "F1" to KeyEvent.KEYCODE_F1,
        "F2" to KeyEvent.KEYCODE_F2,
        "F3" to KeyEvent.KEYCODE_F3,
        "F4" to KeyEvent.KEYCODE_F4,
        "F5" to KeyEvent.KEYCODE_F5,
        "F6" to KeyEvent.KEYCODE_F6,
        "F7" to KeyEvent.KEYCODE_F7,
        "F8" to KeyEvent.KEYCODE_F8,
        "F9" to KeyEvent.KEYCODE_F9,
        "F10" to KeyEvent.KEYCODE_F10,
        "F11" to KeyEvent.KEYCODE_F11,
        "F12" to KeyEvent.KEYCODE_F12,
    )

    /** 键码 → rime 的符号名（`comma`、`numbersign`…），与 [names] 的标点部分互为反表。 */
    private val symbolNames: Map<Int, String> = mapOf(
        KeyEvent.KEYCODE_COMMA to "comma",
        KeyEvent.KEYCODE_PERIOD to "period",
        KeyEvent.KEYCODE_SLASH to "slash",
        KeyEvent.KEYCODE_BACKSLASH to "backslash",
        KeyEvent.KEYCODE_SEMICOLON to "semicolon",
        KeyEvent.KEYCODE_APOSTROPHE to "apostrophe",
        KeyEvent.KEYCODE_GRAVE to "grave",
        KeyEvent.KEYCODE_MINUS to "minus",
        KeyEvent.KEYCODE_EQUALS to "equal",
        KeyEvent.KEYCODE_LEFT_BRACKET to "bracketleft",
        KeyEvent.KEYCODE_RIGHT_BRACKET to "bracketright",
        KeyEvent.KEYCODE_POUND to "numbersign",
        KeyEvent.KEYCODE_STAR to "asterisk",
        KeyEvent.KEYCODE_PLUS to "plus",
        KeyEvent.KEYCODE_AT to "at",
        KeyEvent.KEYCODE_SPACE to "space",
    )

    /** 符号键码 → 字符，用来把 `comma` 渲染成 `,`。 */
    private val symbolLabels: Map<Int, String> = mapOf(
        KeyEvent.KEYCODE_COMMA to ",",
        KeyEvent.KEYCODE_PERIOD to ".",
        KeyEvent.KEYCODE_SLASH to "/",
        KeyEvent.KEYCODE_BACKSLASH to "\\",
        KeyEvent.KEYCODE_SEMICOLON to ";",
        KeyEvent.KEYCODE_APOSTROPHE to "'",
        KeyEvent.KEYCODE_GRAVE to "`",
        KeyEvent.KEYCODE_MINUS to "-",
        KeyEvent.KEYCODE_EQUALS to "=",
        KeyEvent.KEYCODE_LEFT_BRACKET to "[",
        KeyEvent.KEYCODE_RIGHT_BRACKET to "]",
        KeyEvent.KEYCODE_POUND to "#",
        KeyEvent.KEYCODE_STAR to "*",
        KeyEvent.KEYCODE_PLUS to "+",
        KeyEvent.KEYCODE_AT to "@",
        KeyEvent.KEYCODE_SPACE to " ",
    )

    private val symbolCodes: Map<String, Int> =
        symbolNames.entries.associate { (code, name) -> name to code + RimeKeyTable.SYMBOL_CODE_OFFSET }

    // 只认大写：小写字母走 [nameToKeyCode] 的普通键码（`a` 必须是 KEYCODE_A，
    // 带上合成大写码会让 rime 在 ASCII 模式下拒收这个键，退化成提交小写字母）。
    override fun upperNameToCode(name: String): Int? = if (name.length == 1 && name[0] in 'A'..'Z') {
        RimeKeyTable.UPPER_CODE_OFFSET + (name[0] - 'A')
    } else {
        null
    }

    override fun symbolNameToCode(name: String): Int? = symbolCodes[name]

    override fun charToCode(name: String): Int? = if (name.length == 1) nameToKeyCode(name).takeIf { it != 0 } else null

    override fun nameToKeyCode(name: String): Int {
        names[name]?.let { return it }
        // a-z / 0-9 在 rime 与 Android 里拼写一致，直接算。
        if (name.length == 1) {
            val ch = name[0]
            when {
                ch in 'a'..'z' -> return KeyEvent.KEYCODE_A + (ch - 'a')
                ch in '0'..'9' -> return KeyEvent.KEYCODE_0 + (ch - '0')
            }
        }
        // 其余交给宿主可替换的那次查找（单测会把它 stub 掉）。
        return KeyCode.androidKeyNameToCode(name)
    }

    override fun upperCodeToName(code: Int): String? {
        val offset = code - RimeKeyTable.UPPER_CODE_OFFSET
        return if (offset in 0..25) ('A' + offset).toString() else null
    }

    override fun symbolCodeToName(code: Int): String? = symbolNames[code - RimeKeyTable.SYMBOL_CODE_OFFSET]

    /** 名字反查留给调用方（`KeyCode.codeToKeyName` 自己有 Android 那条退路）。 */
    override fun keyCodeToName(code: Int): String? = null

    override fun symbolCodeToLabel(code: Int): String? = symbolLabels[code - RimeKeyTable.SYMBOL_CODE_OFFSET]
}

/** 键名表的当前实现；输入法启动时替换成 `RimeKeyMapping` 那一份。 */
@Volatile
var rimeKeyTable: RimeKeyTable = BasicRimeKeys
