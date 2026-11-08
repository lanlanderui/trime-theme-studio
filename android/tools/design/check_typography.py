#!/usr/bin/env python3
"""N-4：方向 B 三屏的大字规格核对。

稿子（`doc/design/ui-directions.html` 方向 B）：
  B1 英雄区名称 34px / 680  ·  B2「配色」30px / 680  ·  B3 文字大卡值 30px / 640
Android 没有可变字重，实现统一取 bold(700) —— 比 medium(500) 更接近 680/640。
B1/B2 在布局 XML 里声明，B3 的大卡值在 `KeyboardEditorActivity.addBigField`
里动态生成，所以三处分别用 XML 属性与源码正则核对。

用法：python tools/design/check_typography.py   （退出码 0 = 全部符合）
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ANDROID = "{http://schemas.android.com/apk/res/android}"


def xml_attrs(path, view_id):
    tree = ET.parse(os.path.join(ROOT, path))
    for node in tree.iter():
        rid = (node.get(ANDROID + "id") or "").split("/")[-1]
        if rid == view_id:
            return {k.split("}")[-1]: v for k, v in node.attrib.items()}
    return {}


def source_text(path):
    return open(os.path.join(ROOT, path), encoding="utf-8").read()


def check(label, spec, actual, ok):
    print("%-26s 稿 %-14s 实现 %-16s %s" % (label, spec, actual, "OK" if ok else "FAIL"))
    return ok


def main():
    b1 = xml_attrs("app/src/main/res/layout/activity_main.xml", "heroName")
    b2 = xml_attrs("editor-ui/src/main/res/layout/fragment_color_scheme.xml", "title")
    b3 = source_text("app/src/main/kotlin/com/trime/editor/KeyboardEditorActivity.kt")

    results = [
        check(
            "B1 英雄区名称",
            "34sp / 680",
            "%s / %s" % (b1.get("textSize", "?"), b1.get("textStyle", "?")),
            b1.get("textSize") == "34sp"
            and b1.get("fontFamily") == "sans-serif"
            and b1.get("textStyle") == "bold",
        ),
        check(
            "B2 配色标题",
            "30sp / 680",
            "%s / %s" % (b2.get("textSize", "?"), b2.get("textStyle", "?")),
            b2.get("textSize") == "30sp"
            and b2.get("fontFamily") == "sans-serif"
            and b2.get("textStyle") == "bold",
        ),
    ]

    # B3：addBigField 里只认这一个 30f 与 bold，避免和别处的小字撞上
    block = b3[b3.index("private fun addBigField"):]
    block = block[: block.index("private fun ", 10)] if "private fun " in block[10:] else block[:1500]
    results.append(
        check(
            "B3 文字大卡值",
            "30sp / 640",
            "30f / %s" % ("bold" if 'Typeface.BOLD' in block else "medium"),
            "setTextSize(30f)" in block and "Typeface.BOLD" in block,
        )
    )

    print("\n%d / %d 符合" % (sum(results), len(results)))
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
