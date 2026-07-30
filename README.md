# Trime 主题工坊

一个完全离线、可直接双击打开的 Trime 配色与键盘布局预览编辑网页。

> 项目说明：本项目由 **lanlanderui** 提出需求并提供初始 Trime 配置模板，界面设计、功能开发、官方参数整理、自动化测试与项目文档由 **OpenAI Codex** 完成。

## 在线使用

点击进入：[Trime 主题工坊在线版](https://lanlanderui.github.io/trime-theme-studio/)

在线版直接在浏览器中运行，不需要安装，也不会把载入的 YAML 配置上传到服务器；文件解析、预览、修改和导出均在当前浏览器页面内完成。

![Trime 主题工坊界面](trime-theme-studio.png)

## 功能

- 读取和筛选 `preset_color_schemes` 主题方案。
- 实时预览候选栏、键盘、按键按下及弹出层配色。
- 编辑官方颜色字段，兼容 `0xAARRGGBB`、YAML anchor 与背景图片路径。
- 新增、删除、拖动和调整按键宽度，编辑点击、长按与四向滑动动作。
- 支持撤销、重置，并导出保留其余配置内容的完整 `.trime.yaml`。

## 使用

1. 双击 `index.html`。
2. 从左侧选择 `preset_color_schemes` 中的主题。
3. 在右侧点击色块或输入 `0xAARRGGBB` 调色；中间键盘会实时更新。
4. 切换到“键盘布局”后，可以在中间预览中点击或拖动按键；右侧支持新增、删除、前后移动，并可编辑 `click`、`label`、`width`、`long_click` 和四向滑动动作。
5. 点击右上角“导出”，下载保留其余配置与注释的完整 `.trime.yaml`。

布局按 Trime 的宽度规则实时换行：每行累计到约 100% 后进入下一行。因此调整顺序决定按键先后位置，调整 `width` 决定占位宽度。完整配置导出会写入布局修改；“复制当前主题 YAML”和“下载当前主题片段”只包含配色方案。

网页预置了同目录的 `trime.yaml`。也可以把任意 UTF-8 编码的 `.trime.yaml` 拖到网页中临时载入，网页不会直接改写源文件。`tongwenfeng.trime.yaml` 保留在目录中作为备用模板。

如果手动更新了同目录的 `trime.yaml`，运行下面的命令可以更新网页内置副本：

```powershell
node build-bundled-config.mjs
```

也可以临时指定其他模板，例如：

```powershell
node build-bundled-config.mjs tongwenfeng.trime.yaml
```

项目没有在线依赖，断网也能使用。

## GitHub Pages 发布方式

本项目是纯静态网页，入口文件为仓库根目录的 `index.html`，不需要后端服务器和构建步骤。仓库使用 GitHub Pages 的 **Deploy from a branch** 模式，将 `main` 分支的 `/ (root)` 目录作为网站来源。GitHub 会自动把 HTML、CSS、JavaScript 和内置模板发布到：

```text
https://lanlanderui.github.io/trime-theme-studio/
```

以后向 `main` 分支推送更新时，GitHub Pages 会自动重新部署，通常等待片刻后在线版就会更新。

## 相关项目与文档

- [Trime（同文输入法）](https://github.com/osfans/trime)
- [Trime 主题配置文档](https://github.com/osfans/trime/wiki/Theme-Configuration-(New))

## 颜色字段兼容性

颜色编辑器依据 Trime `develop` 分支的内置 fallback 清单维护。当前按键弹出层使用：

- `popup_back_color`
- `popup_text_color`
- `hilited_popup_back_color`
- `hilited_popup_text_color`

旧配置中的 `preview_back_color`、`preview_text_color` 会继续显示，但会标记为已废弃并提示新的替代字段。`root_background`、`candidate_background`、`keyboard_background` 等背景项也支持直接保留图片路径。

## 许可

本项目代码采用 [MIT License](LICENSE)。Trime 及配置模板中属于第三方的内容仍遵循其各自原有的许可和版权声明。
