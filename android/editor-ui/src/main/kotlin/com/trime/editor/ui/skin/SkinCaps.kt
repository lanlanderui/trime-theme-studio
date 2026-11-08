/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.skin

/**
 * 皮肤允许改变「结构」的那几个开关。
 *
 * 页面里唯一允许出现的皮肤分支就是读这几个布尔值；除此之外不许出现
 * `if (skin == EXPRESSIVE)`——一旦页面开始认皮肤名字，第四套皮肤就得改九个页面。
 *
 * 为什么做成数据而不是直接问资源：`:editor-ui` 里的组件看不见 `:app` 的皮肤枚举
 * （依赖方向是 app → editor-ui），只能由 app 在启动时把当前皮肤的能力位塞进来。
 */
data class SkinCaps(
    /** 顶栏换成「色块舞台」：整块主色 + 大标题 + 大圆角收边。 */
    val stageHeader: Boolean = false,
    /** 底部动作条换成悬浮胶囊（拇指更容易够到，也不再占一整行）。 */
    val floatingDock: Boolean = false,
    /** 分组导航换成胶囊段控（而不是分节标题）。 */
    val segmented: Boolean = false,
)

/** 当前皮肤的能力位。由 `:app` 在 Activity 起来时写入，组件只读。 */
object UiMode {
    @Volatile
    var caps: SkinCaps = SkinCaps()
}
