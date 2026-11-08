/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.workbench

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Files

/**
 * N-1 / N-2 里不碰 Android 的那部分：命名规则与 `name:` 行改写。
 *
 * 主题文件是用户手写的，写盘铁律是「除目标行外逐字节不变」。这两件事一旦
 * 错了，症状不是崩溃而是注释悄悄没了、行尾被换成 LF——所以钉成用例。
 */
class ThemeActionsTest :
    BehaviorSpec({
        fun tempDir(): File = Files.createTempDirectory("theme-actions").toFile()

        given("显示名与文件 id 的换算") {
            then("去掉 .trime 后缀") {
                ThemeActions.displayBase("tongwenfeng.trime") shouldBe "tongwenfeng"
                ThemeActions.displayBase("template.trime") shouldBe "template"
            }
        }

        given("新建 / 复制时挑名字") {
            then("不冲突就用原名") {
                val dir = tempDir()
                ThemeActions.uniqueBase(dir, "新建主题") shouldBe "新建主题"
            }
            then("已存在就顺延，不覆盖") {
                val dir = tempDir()
                File(dir, "新建主题.trime.yaml").writeText("x")
                File(dir, "新建主题2.trime.yaml").writeText("x")
                ThemeActions.uniqueBase(dir, "新建主题") shouldBe "新建主题3"
            }
            then("路径分隔符等非法字符换成下划线，空名兜底") {
                val dir = tempDir()
                ThemeActions.uniqueBase(dir, "a/b:c") shouldBe "a_b_c"
                ThemeActions.uniqueBase(dir, "   ") shouldBe "主题"
            }
        }

        given("改写模板的 name 行") {
            val source =
                listOf(
                    "# 用户注释别动",
                    "name: 模版主题",
                    "  style:",
                    "    name: 这个不能改",
                ).joinToString("\r\n").toByteArray(Charsets.UTF_8)

            then("只改第一处，注释 / 缩进 / CRLF 全留") {
                val out = String(ThemeActions.withName(source, "我的主题"), Charsets.UTF_8)
                out shouldContain "# 用户注释别动\r\n"
                out shouldContain "name: \"我的主题\"\r\n  style:\r\n    name: 这个不能改"
                out shouldNotContain "name: 模版主题"
                out.split("\r\n").size shouldBe 4
            }

            then("没有 name 行就原样返回") {
                val plain = "style:\r\n  key: 1\r\n".toByteArray(Charsets.UTF_8)
                ThemeActions.withName(plain, "x") shouldBe plain
            }

            then("名字里的引号被转义，不会写出坏 YAML") {
                val out = String(ThemeActions.withName(source, "a\"b"), Charsets.UTF_8)
                out shouldContain "name: \"a\\\"b\""
            }

            then("内置模板 name 行的行内注释与间距都保留") {
                val line = "name: 模版主题            # 主题名称，显示在主题列表里"
                val src = ("# head\r\n" + line + "\r\nstyle:\r\n").toByteArray(Charsets.UTF_8)
                val out = String(ThemeActions.withName(src, "本地主题"), Charsets.UTF_8)
                out shouldContain "name: \"本地主题\"            # 主题名称，显示在主题列表里"
                out shouldContain "# head\r\n"
                out shouldContain "\r\nstyle:\r\n"
            }
        }

        given("N-2 重命名的文件级动作") {
            fun seed(dir: File): ByteArray {
                val bytes =
                    listOf("# 注释", "name: 旧主题", "style:", "  key_height: 48")
                        .joinToString("\r\n").toByteArray(Charsets.UTF_8)
                File(dir, "old.trime.yaml").writeBytes(bytes)
                return bytes
            }

            then("改名只动 name 行，原文件消失") {
                val dir = tempDir()
                seed(dir)
                val r = ThemeActions.renameFile(dir, "old.trime", "新名字")
                r.ok shouldBe true
                r.newId shouldBe "新名字.trime"
                File(dir, "old.trime.yaml").exists() shouldBe false
                val out = String(File(dir, "新名字.trime.yaml").readBytes(), Charsets.UTF_8)
                out shouldContain "# 注释\r\nname: \"新名字\"\r\nstyle:\r\n  key_height: 48"
            }

            then("同名 / 空名直接拒绝，不写盘") {
                val dir = tempDir()
                seed(dir)
                ThemeActions.renameFile(dir, "old.trime", "old").error shouldBe ThemeActions.RenameError.UNCHANGED
                ThemeActions.renameFile(dir, "old.trime", "   ").error shouldBe ThemeActions.RenameError.UNCHANGED
                File(dir, "old.trime.yaml").exists() shouldBe true
            }

            then("目标已存在时不覆盖") {
                val dir = tempDir()
                seed(dir)
                File(dir, "taken.trime.yaml").writeText("别人的主题")
                ThemeActions.renameFile(dir, "old.trime", "taken").error shouldBe ThemeActions.RenameError.EXISTS
                File(dir, "taken.trime.yaml").readText() shouldBe "别人的主题"
            }

            then("路径分隔符不会跑出目录") {
                val dir = tempDir()
                seed(dir)
                val r = ThemeActions.renameFile(dir, "old.trime", "a/b")
                r.ok shouldBe true
                r.newId shouldBe "a_b.trime"
            }
        }
    })
