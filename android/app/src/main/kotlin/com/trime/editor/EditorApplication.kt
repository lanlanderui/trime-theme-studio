/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.app.Application
import android.content.res.Configuration
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.util.isNightMode
import timber.log.Timber

class EditorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // 先记录再交给系统：日志要留给下一次启动解释「上次为什么没了」。
        CrashLog.install(this)
        // 共享库的接缝：没有 librime 的那一套实现
        EditorPrefs.install(this)
        EditorServices.install()
        // `init` 不只是记日夜：它还建了图片缓存，主题里 image 型颜色靠它解码。
        ColorManager.init(resources.configuration)
    }

    /** 跟随系统日夜时配色要跟着走；预览页的「跟随系统」读的就是这个值。 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ColorManager.onSystemNightModeChange(newConfig.isNightMode())
    }
}
