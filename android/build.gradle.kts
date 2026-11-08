// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        // The Gradle user home lives inside the project here: without this the
        // check never passes and stops being a gate.
        targetExclude("**/build/**", ".gradle-user-home/**", ".tmp/**")
        ktlint("1.8.0")
        // `.editorconfig` asks for LF, so the check must too. Otherwise the
        // expected endings follow `core.autocrlf` and every file on a Windows
        // checkout is reported as modified on every line.
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
    }
}
