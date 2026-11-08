/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.TextKeyboard

/**
 * The keyboard the IME would show for [theme].
 *
 * Mirrors `KeyboardWindow.selectKeyboardConfig`: `default` first, then the
 * stock `qwerty`, then whatever the theme defines — following `importPreset`
 * so a layout that only borrows its keys still resolves.
 *
 * A fixed id cannot be used: a single-hand theme names its main keyboard
 * `default` and may not define `qwerty` at all.
 *
 * Every preview board goes through this, so what they draw cannot be a
 * different keyboard from the one the IME raises for the same theme.
 */
fun resolveMainKeyboardConfig(theme: Theme): TextKeyboard? {
    fun withImport(
        name: String,
        depth: Int,
    ): TextKeyboard? {
        if (depth > MAX_IMPORT_DEPTH) return null
        val config = theme.presetKeyboards[name] ?: return null
        val import = config.importPreset
        if (import.isNullOrEmpty() || import == name) return config
        val base = withImport(import, depth + 1)
        return if (config.keys.isEmpty() && base != null) base else config
    }

    return withImport(MAIN_KEYBOARD_ID, 0)
        ?: withImport(STOCK_KEYBOARD_ID, 0)
        ?: theme.presetKeyboards.keys
            .firstOrNull { theme.presetKeyboards[it]?.keys?.isNotEmpty() == true }
            ?.let { withImport(it, 0) }
}

/**
 * The label the Enter key shows on a plain text field.
 *
 * `enter_labels.default` is the theme's own wording; the model keeps the literal
 * `"default"` when the theme never set one, so that case falls back to the
 * return glyph the IME draws. Every preview board shares this so they cannot
 * drift apart.
 */
fun enterLabelOf(theme: Theme): String = theme.style.enterLabels.default.let { if (it == DEFAULT_ENTER_PLACEHOLDER) RETURN_GLYPH else it }

private const val MAIN_KEYBOARD_ID = "default"
private const val STOCK_KEYBOARD_ID = "qwerty"
private const val MAX_IMPORT_DEPTH = 5
private const val DEFAULT_ENTER_PLACEHOLDER = "default"
private const val RETURN_GLYPH = "\u23CE"
