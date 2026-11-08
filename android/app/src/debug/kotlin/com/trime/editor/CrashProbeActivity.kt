/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.os.Bundle
import com.trime.editor.ui.EditorActivity

/**
 * 只在调试包里存在：打开就抛一次没人接的异常。
 *
 * 用来验「崩溃日志」这条链路本身——`CrashLog.format` 有单测，但「真的崩了会不会
 * 落盘」「下次启动会不会弹提示」只能靠一次真实崩溃。`am start` 这个页面即可：
 *
 * ```bash
 * adb shell am start -n com.trime.editor.debug/com.trime.editor.CrashProbeActivity
 * adb shell run-as com.trime.editor.debug cat files/crash.log
 * ```
 *
 * 发布包没有这个类，也没有对应的清单条目（都在 `src/debug`）。
 */
class CrashProbeActivity : EditorActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 在 onCreate 里抛，而不是字段初始化：那样栈顶会落在类初始化上，
        // 看不出是哪一页。这里异常会一路冒到 CrashLog 包的默认 handler。
        throw IllegalStateException("CrashProbe: 调试包故意制造的一次崩溃")
    }
}
