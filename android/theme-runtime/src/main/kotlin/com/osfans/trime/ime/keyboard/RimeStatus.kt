/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * 按键渲染要问 librime 的那几件事。
 *
 * 原本 `Key` 和 `KeyAction` 直接 `RimeDaemon.getFirstSessionOrNull()!!`，那是一路
 * librime 会话；编辑器没有 librime，但它的预览板要画同样的键，所以只能问这个接口。
 *
 * 输入法把它接到 `RimeApi`（每次读取都现问，与搬出来之前一致）；编辑器装一个恒定的
 * 实现——预览里的键不该因为后台有没有引擎而变样。
 */
interface RimeStatus {
    val isAsciiMode: Boolean

    val isAsciiPunct: Boolean

    val isComposing: Boolean

    /** 候选区当前有菜单（有候选）。键可以只在有候选时才有动作。 */
    val hasMenu: Boolean

    /** 候选区正在翻页。 */
    val paging: Boolean

    /** 当前方案名；空格键在没有 label 时用它当显示名。 */
    val schemaName: String

    /** 方案 id；[schemaName] 为空时拿它兜底。 */
    val schemaId: String

    /** 运行时开关（`toggle` 型按键的当前状态）。 */
    fun getRuntimeOption(option: String): Boolean
}

/**
 * 当前装着的 [RimeStatus]。
 *
 * 不注入就抛异常：给个「全 false」的默认值会让输入法的键看起来都正常、只是永远
 * 显示中文标签，这种错最难从截图上发现。
 */
object RimeStatusProvider {
    @Volatile
    var status: RimeStatus? = null

    fun require(): RimeStatus = status ?: error("RimeStatusProvider.status has not been installed by the host")
}
