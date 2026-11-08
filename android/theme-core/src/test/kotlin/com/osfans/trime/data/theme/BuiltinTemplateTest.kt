/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * 随包内置模版（用户手工维护的全量参数模版）必须能被编辑器自己的 YAML 解析器读开。
 *
 * 它比测试夹具更接近真实输入：有注释、锚点、flow map、演示用的 `Demo_*` 预设键。
 * 模版换了却没人发现读不开时，症状是「另存到本地之后预览页白屏」，所以在这里钉住。
 * 文件仍放在 `src/main/assets/templates/`，app 通过 assets 源集打进 APK，
 * 不复制第二份，避免两边漂移。
 */
class BuiltinTemplateTest :
    BehaviorSpec({
        fun template(): File {
            val candidates =
                listOf(
                    "src/main/assets/templates/template.trime.yaml",
                    "../theme-core/src/main/assets/templates/template.trime.yaml",
                ).map(::File)
            return candidates.firstOrNull { it.isFile }
                ?: error("找不到内置模版，试过：${candidates.joinToString { it.absolutePath }}")
        }

        given("随包内置模版") {
            val file = template()
            val bytes = file.readBytes()
            val text = String(bytes, Charsets.UTF_8)

            then("是完整的 LF 文本，没有 BOM") {
                bytes.size shouldBeGreaterThan 30_000
                (bytes.size >= 3 && bytes[0] == 0xEF.toByte()) shouldBe false
                text.contains("\r\n") shouldBe false
            }

            then("顶层段落齐全，解析器能读开") {
                val root = ThemeYaml.parser.parseToYamlNode(text) as YamlMap
                val keys = root.entries.keys.map { (it as YamlScalar).content }
                listOf(
                    "config_version",
                    "name",
                    "style",
                    "window",
                    "tool_bar",
                    "preset_keys",
                    "preset_keyboards",
                    "preset_color_schemes",
                    "fallback_colors",
                ).forEach { keys shouldContain it }
            }

            then("配色方案里仍有完整色键表") {
                val root = ThemeYaml.parser.parseToYamlNode(text) as YamlMap
                val schemes = root.value("preset_color_schemes") as YamlMap
                val first = schemes.entries.values.first() as YamlMap
                first.entries.size shouldBeGreaterThan 50
            }

            then("name 行带行内注释，另存时要保得住") {
                text shouldContain "name: 模版主题            # 主题名称，显示在主题列表里"
            }
        }
    })

private fun YamlMap.value(key: String): YamlNode = entries.entries.first { (k, _) -> (k as YamlScalar).content == key }.value
