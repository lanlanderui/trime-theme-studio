# 同文动态配色 Android App

这是一个独立的原生 Android 小工具，不使用 WebView，也不依赖网络。它读取 Android 12 及以上版本由壁纸生成的 Material You 系统调色板，并生成可用于同文输入法（Trime）的 YAML 配色代码。

## 功能

- 读取系统 `accent1`、`accent2`、`accent3`、`neutral1`、`neutral2` 动态色板。
- 可切换到“自选主色”，通过色相、鲜艳度或十六进制色值指定主色，自动补全浅色、深色及功能键颜色。
- 预览浅色与深色键盘效果。
- 同时生成互相关联的 `material_you` 与 `material_you_dark` 两套方案。
- 从 Rime 目录自由选择现有 `.trime.yaml` / `.yaml` 文件并一键写入。
- 只更新 App 管理的两套动态配色，保留键盘布局、注释、YAML anchor 和其它方案。
- 写入前在 App 私有目录保存最近备份，可直接撤销上次写入。
- 可将喜欢的当前配色永久追加为独立方案；之后更新动态配色不会覆盖收藏方案。
- 提供可由同文 `command: run` 直接启动的快速更新组件；写入后自动请求同文重新部署。
- 可将“更新配色”和“收藏配色”两个快捷键定义一键写入所选 YAML。
- 一键复制、分享，或通过系统文件选择器导出 `material_you.trime.yaml`。
- Android 11 及以下版本使用内置 Material 3 备用配色。
- 不申请存储权限；导出由 Android Storage Access Framework 完成。

## 构建

用 Android Studio 打开本目录并构建，或在 PowerShell 中运行：

```powershell
./gradlew.bat assembleDebug
```

调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 使用生成结果

推荐直接在 App 内点击“选择 YAML”，从 Rime 目录选择同文主题文件，再点击“一键写入”。App 会在现有 `preset_color_schemes` 中新增或更新 `material_you` 和 `material_you_dark`；如果该区块尚不存在，则自动创建。写入后在同文输入法里重新部署，并选择 `material_you` 配色。

需要保留某次配色时，点击“永久保存当前配色”并输入收藏名称。App 会生成唯一的 `saved_时间戳` 浅色/深色方案并追加到同一份 YAML；以后“一键写入”只更新 `material_you`，不会改动这些收藏。自定义配色可在“配色来源”中选择“自选主色”，拖动色相和鲜艳度滑块，或直接输入 `#RRGGBB` 色值，其余语义色由 App 自动生成。

系统文件选择器授予的读写权限会被记住，之后更换壁纸配色时，只需打开 App、刷新颜色并再次点击“一键写入”。无需授予整个存储空间的访问权限。

## 同文键盘一键更新

首次在 App 中选择主题 YAML 后，点击“一键写入快捷键配置”，App 会把下面两个定义安全加入主题的 `preset_keys`；若已存在则更新，其他快捷键保持不变：

```yaml
DynamicColor:
  command: run
  label: "更新配色"
  option: "com.lanlanderui.trimedynamiccolors/.QuickApplyActivity"
```

再把 `DynamicColor` 绑定到需要的键位。点击该键会启动透明的快速入口，按照 App 当前选择的配色来源（系统动态色或自选主色）更新此前选择的 YAML、请求同文重新部署，然后自动返回。

也可以添加一个“永久收藏当前配色”的按键：

```yaml
SaveDynamicColor:
  command: run
  label: "收藏配色"
  option: "com.lanlanderui.trimedynamiccolors/.QuickSaveActivity"
```

“一键写入快捷键配置”也会同时写入 `SaveDynamicColor`。把它绑定到需要的键位后，点击即可把当前来源生成的配色按“方案名 + 收藏时间”自动命名，作为独立的浅色/深色方案追加到已选择的 YAML，并请求同文重新部署。它不会覆盖 `material_you`、既有收藏或其它方案。

如果文件授权失效或尚未选择文件，快速入口会提示并打开主界面。快速入口不接受外部传入的文件路径或 YAML 内容，只操作 App 内此前授权并记住的文件。

动态颜色由 Android 12 引入。系统会根据壁纸或“壁纸与样式”中选择的颜色生成调色板；更换后回到 App 点击“刷新”即可重新读取。
