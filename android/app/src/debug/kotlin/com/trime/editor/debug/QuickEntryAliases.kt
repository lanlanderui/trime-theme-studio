/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.debug

/**
 * 调试包的快捷入口别名。
 *
 * `option: "包名/.QuickApplyActivity"` 里的 `.类名` 是按 **applicationId** 展开的
 * （`ComponentName.unflattenFromString`）：正式包展开成 `com.trime.editor.QuickApplyActivity`
 * 正好是主源集那个类；调试包的 applicationId 多了 `.debug`，展开后会去
 * `com.trime.editor.debug` 找同名类。这两个空壳类就是给调试包用的，
 * 让同一份主题的写法在两种包上都能点得动。
 *
 * 只在 debug 源集：发布包里没有它们，也不会多出两个入口。
 */
class QuickApplyActivity : com.trime.editor.QuickApplyActivity()

class QuickSaveActivity : com.trime.editor.QuickSaveActivity()
