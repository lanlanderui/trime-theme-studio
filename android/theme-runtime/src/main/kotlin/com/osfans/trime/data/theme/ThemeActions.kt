/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.keyboard.KeyAction
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * `Theme` 上按 token 取 [KeyAction] 的入口。
 *
 * 一开始是 `Theme` 的成员，D1b 把 `Theme` 搬进共享库时挪了出来（那时 `KeyAction`
 * 还留在输入法里）；D1c 把 `KeyAction` 也搬进来之后，它就回到同一模块，只是仍然
 * 写成扩展函数——`Theme` 是可序列化的配置模型，动作解析带缓存和 rime 状态，
 * 混在模型里会让「这个 data class 到底是不是纯数据」变得说不清。
 *
 * 缓存是 per-theme 的弱引用表：`ThemeManager` 换掉旧主题后要能被回收；而切回同一个
 * 主题时拿到的是同一个 `Theme` 实例，缓存继续有效。
 */
private val actionCaches: MutableMap<Theme, MutableMap<KeyActionToken, KeyAction>> =
    Collections.synchronizedMap(WeakHashMap())

fun Theme.resolveAction(token: KeyActionToken): KeyAction = actionCaches
    .getOrPut(this) { ConcurrentHashMap() }
    .getOrPut(token) { KeyAction(token, presetKeys) }

fun Theme.resolveAction(tokenString: String): KeyAction = resolveAction(KeyActionToken.Plain(tokenString))
