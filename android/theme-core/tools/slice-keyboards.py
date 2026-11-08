"""把真实主题里的 preset_keyboards 段切出来，做成 theme-core 的测试夹具。

用法：python slice-keyboards.py <theme.yaml> <out.yaml>

按 ThemeKeyboardReader.sectionRange 的语义取段：从 `preset_keyboards:` 的下一行起，
到第一个「顶格且既不是空行也不是注释」的行为止。**行尾原样保留**——仓库里那份
夹具是 CRLF，往返测试就是靠这一点证明写盘不会把行尾换掉。

夹具头部那四行注释会被读写一遍（ThemeTextFile 按 CRLF 重新拼行），所以这里也必须
用 CRLF 写，否则「不改任何键」的往返会在头部产生 4 行差异。
"""

import hashlib
import sys


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    raw = open(src, "rb").read()
    lines = raw.decode("utf-8").splitlines(keepends=True)

    start = next((i for i, l in enumerate(lines) if l.rstrip("\r\n") == "preset_keyboards:"), None)
    if start is None:
        raise SystemExit("no preset_keyboards section")

    end = len(lines)
    for i in range(start + 1, len(lines)):
        body = lines[i].rstrip("\r\n")
        if not body.strip() or body.lstrip().startswith("#"):
            continue
        if not body.startswith((" ", "\t")):
            end = i
            break

    body = lines[start:end]
    keys = [l for l in body if l.lstrip().startswith("- {")]
    layouts = [
        l for l in body
        if l.startswith("  ") and not l.startswith("   ") and l.rstrip("\r\n").endswith(":")
    ]

    header = [
        "# 真实主题的 preset_keyboards 段，供 ThemeKeyboardRoundTripTest 做逐字节往返。",
        f"# 来源：{src.split('/')[-1]}（sha256 {hashlib.sha256(raw).hexdigest()[:16]}…，"
        f"共 {len(raw)} 字节 / {len(lines)} 行）",
        f"# 摘取第 {start + 1}–{end} 行，原样保留行尾；{len(layouts)} 个布局、{len(keys)} 条键行。",
        "# 只改这里不会改到任何主题——它是测试夹具，不是模板。",
    ]
    with open(dst, "wb") as f:
        f.write(("\r\n".join(header) + "\r\n").encode("utf-8"))
        f.write("".join(body).encode("utf-8"))

    print(f"layouts={len(layouts)} keys={len(keys)} lines={len(body)}")


if __name__ == "__main__":
    main()
