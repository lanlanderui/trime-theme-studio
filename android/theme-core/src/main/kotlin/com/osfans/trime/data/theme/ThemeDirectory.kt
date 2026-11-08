/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File

/**
 * 主题所在的两个目录，由宿主在启动时注入。
 *
 * - 编辑器：`themeDir` = 用户选定的外部目录，`sharedDir` = 内置示例目录。
 * - 输入法：`themeDir` = `DataManager.themeDir`（可能就是用户切过去的外部目录），
 *   `sharedDir` = assets 镜像目录。
 *
 * 未注入时所有「找主题文件」的接口返回 null，而不是猜一个路径——猜错的症状是
 * 「改了但看不见」，比直接失败更难查（R14）。
 */
object ThemeDirectory {
    @Volatile
    private var fixedThemeDir: File? = null

    /**
     * 目录会随设置变化时用它。
     *
     * 输入法的「主题目录」可以由用户在运行中切到外部目录，读一次就存下来的话，
     * 切换之后写盘还在旧目录里，症状正是 R14 那种「改了但看不见」。所以每次读取
     * 都重新问一遍。
     */
    @Volatile
    private var themeDirProvider: (() -> File?)? = null

    /** 当前主题目录；两个来源各自 `@Volatile`，这里只是取值分派。 */
    var themeDir: File?
        get() = themeDirProvider?.invoke() ?: fixedThemeDir
        set(value) {
            fixedThemeDir = value
            themeDirProvider = null
        }

    @Volatile
    var sharedDir: File? = null

    /** 见 [themeDirProvider]；`null` 表示回到固定值。 */
    fun themeDirFrom(provider: (() -> File?)?) {
        themeDirProvider = provider
    }

    /**
     * librime 的部署产物在哪里。
     *
     * 输入法把它接到 `DataManager.resolveDeployedResourcePath`；编辑器没有 librime，
     * 保持 null——`ThemeLoader` 于是不会走「回退到部署产物」那条分支，
     * 也就不会出现「明明读的是文件、渲染的却是旧产物」。
     */
    @Volatile
    var deployedResolver: ((String) -> File?)? = null

    /**
     * 让 librime 把某个资源部署成产物，返回它是否已是最新。
     *
     * 输入法把它接到 `Rime.deployRimeConfigFile`；编辑器没有 librime，保持 null——
     * `ThemeLoader` 于是直接报告「无从部署」，而不是拿一个不存在的路径去读。
     */
    @Volatile
    var deployer: ((themeId: String, versionKey: String) -> Boolean)? = null

    /** 资源查找的目录顺序：用户目录优先，其次随包示例。未注入的目录被跳过。 */
    fun roots(): List<File> = listOfNotNull(themeDir, sharedDir)

    /** 用户可写的那一份；存在即优先。 */
    fun userFile(themeId: String): File? = themeDir?.let { File(it, "$themeId.yaml") }

    /** 只读的随包示例。 */
    fun sharedFile(themeId: String): File? = sharedDir?.let { File(it, "$themeId.yaml") }

    /** 目录下的任意文件（`<id>.custom.yaml` 这类）。 */
    fun userChild(name: String): File? = themeDir?.let { File(it, name) }

    fun sharedChild(name: String): File? = sharedDir?.let { File(it, name) }

    fun deployedFile(resourceId: String): File? = deployedResolver?.invoke(resourceId)
}
