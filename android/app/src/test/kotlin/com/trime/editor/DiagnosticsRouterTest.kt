/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * What the diagnostics list promises when a row is tapped.
 *
 * The cases are the paths the checks in `ThemeDiagnostics` actually emit, so
 * the test fails if one of them starts falling through to [EditorScreen.NONE]
 * — which on screen is a row that explains a problem and then does nothing.
 */
class DiagnosticsRouterTest :
    BehaviorSpec({
        given("a path that names a section with its own page") {
            then("style settings open the style page") {
                DiagnosticsRouter.route("style/candidate_text_size").screen shouldBe EditorScreen.STYLE
            }

            then("all three colour sections open the colour page") {
                listOf(
                    "preset_color_schemes/default/light_scheme",
                    "fallback_colors/key_back_color",
                    "colors/key_back_color",
                ).map { DiagnosticsRouter.route(it).screen } shouldBe List(3) { EditorScreen.COLORS }
            }

            then("a toolbar finding opens the toolbar page") {
                DiagnosticsRouter.route("tool_bar/button_spacing").screen shouldBe EditorScreen.TOOL_BAR
            }

            then("window and preedit both open the candidate window page") {
                val screens =
                    listOf("window/insets/vertical", "preedit/comment_height")
                        .map { DiagnosticsRouter.route(it).screen }
                screens shouldBe List(2) { EditorScreen.CANDIDATE_WINDOW }
            }
        }

        given("a path that names one entry") {
            then("a keyboard id opens that keyboard, not the list") {
                val route = DiagnosticsRouter.route("preset_keyboards/Keyboard_letter/import_preset")
                route.screen shouldBe EditorScreen.KEYBOARD_EDITOR
                route.id shouldBe "Keyboard_letter"
            }

            then("a preset key id becomes the list's search text") {
                val route = DiagnosticsRouter.route("preset_keys/Keyboard_letter")
                route.screen shouldBe EditorScreen.PRESET_KEYS
                route.id shouldBe "Keyboard_letter"
            }

            then("the id is dropped where the screen cannot use it") {
                DiagnosticsRouter.route("preset_color_schemes/default/light_scheme").id shouldBe ""
                DiagnosticsRouter.route("window/insets/vertical").id shouldBe ""
            }
        }

        given("a path that names a whole section") {
            then("a section-wide finding opens the list, not one entry") {
                val route = DiagnosticsRouter.route("preset_keyboards")
                route.screen shouldBe EditorScreen.KEYBOARD_LIST
                route.id shouldBe ""
            }

            then("a trailing slash changes nothing") {
                DiagnosticsRouter.route("preset_keyboards/").screen shouldBe EditorScreen.KEYBOARD_LIST
            }
        }

        given("a path nothing owns") {
            then("a finding without a path has no route") {
                DiagnosticsRouter.route(null).screen shouldBe EditorScreen.NONE
                DiagnosticsRouter.route("").screen shouldBe EditorScreen.NONE
            }

            then("a top-level setting has no page") {
                DiagnosticsRouter.route("config_version").screen shouldBe EditorScreen.NONE
            }

            then("an unknown top-level key has no page, with or without the root slash") {
                DiagnosticsRouter.route("height").screen shouldBe EditorScreen.NONE
                DiagnosticsRouter.route("/height").screen shouldBe EditorScreen.NONE
            }

            then("the root slash does not hide a real section behind it") {
                DiagnosticsRouter.route("/style/key_height").screen shouldBe EditorScreen.STYLE
            }
        }
    })
