# 同文编辑器 · UI 皮肤化改造开发计划

> 结论：把界面拆成 **一套皮肤接口 + 三套皮肤实现**（`proof` 旧观感 / `m3-tonal` 打底 / `m3-expressive` 可选装）。
> 先用 P0 把「切皮肤」这条管道铺通，并保证在 `proof` 皮肤下**画面逐像素不变**；再逐页换装 A；
> 最后 B 只加一张 token 表 + 三个能力位，不改页面代码。
>
> 配套效果图：`doc/design/ui-directions.html`（方案 A / B / C 三屏对照，含亮暗切换）。

---

## 1. 命名：先解决一个撞名问题

仓库里已经有一套叫「**A 方案「校样」**」的视觉体系（`proof_*` 资源，纸感 + 衬线标题 + 朱红强调），
它是**现在的**界面。效果图里的 A/B 是**新的**两套。两套都叫 A，会直接把计划读错。

因此全文只用 skin id，不再用字母：

| skin id | 中文名 | 定位 | 主题资源 |
|---|---|---|---|
| `proof` | 校样 | 现有观感，改造期间保留，用于对照与回退 | `Theme.TrimeEditor.Skin.Proof` |
| `m3-tonal` | 层叠 | **默认**，打底方案（效果图 A） | `Theme.TrimeEditor.Skin.Tonal` |
| `m3-expressive` | 舞台 | 可选装皮肤（效果图 B） | `Theme.TrimeEditor.Skin.Expressive` |

`proof` 的去留不在本次决定范围内：改造期它与新皮肤共用同一套 token 接口，保留成本≈0；
等 `m3-tonal` 真机跑顺两周后，再单独决定「删掉 / 作为怀旧皮肤留着」。

---

## 2. 现状盘点（决定工作量的四个事实）

| 事实 | 数据 | 对计划的影响 |
|---|---|---|
| 已有 token 先例 | `proof_colors.xml`（11 色）、`proof_dimens.xml`（17 尺寸）、`proof_styles.xml`（6 字阶）、`values-night` 与 `values-sw600dp` 覆盖 | **不要另起一套**：把 `proof_*` 视为第一套皮肤的实现，新皮肤补同名字段 |
| token 引用面 | 源码内 `proof_*` 共 **269 处**，分布在 **42 个文件**（29 个布局 + 13 个 Kotlin） | 换装靠「改 token 值」而非「改页面」，否则就是 269 处手工替换 |
| 视图构建方式 | 纯 View：布局 XML + Splitties DSL 代码拼视图 + 自绘 Canvas（`KeyboardCanvasView` / `ThemeBoard` / `ColorPickerView` / `SwatchStripView`） | 光靠 XML 主题不够，**必须有一层代码侧 token 读取器** |
| Activity 无基类 | 13 个 Activity 全部直接继承 `AppCompatActivity`，其中 3 个快捷入口在清单里挂了 `Theme.TrimeEditor.QuickAction`（半透明，不画界面） | P0 要引入基类，且**不能覆盖清单主题**，否则快捷入口会闪白屏 |
| 门禁 | `spotlessKotlinCheck`(根) + `testDebugUnitTest` + `lintDebug`(0 error) + `assembleDebug`；四合一超 10 分钟会被 SIGTERM，拆两次跑 | 每阶段退出条件沿用同一套，不新造流程 |
| 数据层 | `theme-core` 的写盘/解析/撤销已有逐字节闸门 | **本次一行都不许改 `theme-core`**，任何"顺手"的数据层改动都是范围蔓延 |

---

## 3. 架构

### 3.1 三层，各自只管一件事

```
主题资源层 (res/values*)        皮肤声明：颜色 / 形状 / 字阶 / 尺寸 / 能力位
        │  ThemeOverlay
        ▼
token 解析层 (ui/skin/)          UiTokens.color(...) / .dimen(...) / .shape(...)
        │                        + Palette（运行时取色，见 §3.4）
        ▼
组件层 (ui/kit/)                  TopBar / Card / Row / Field / ActionBar / Chip / Dock …
        │
        ▼
页面 (13 个 Activity)            只调组件，页内不出现颜色、圆角、字号字面量
```

三条硬规则，写进代码注释，评审时按这三条卡：

1. **页面里不许出现 `R.color.*`、`"#RRGGBB"`、`16.dp` 这类具体值**——一律走 `UiTokens` 或布局里的 `?attr/`。
2. **页面里不许出现 `if (skin == EXPRESSIVE)`**——差异要么是 token，要么是组件能力位（§3.3）。
3. **`theme-core` 与预览绘制不动**：键盘预览必须继续复用输入法的 `KeyFaceRenderer`，这是像素一致的前提（见 03 期 D2 闸门）。

### 3.2 颜色走 M3 标准属性，形状/尺寸/字阶走自定义属性

这是本次最关键的一个技术选择：

| 类别 | 用什么 | 为什么 |
|---|---|---|
| 颜色 | **直接用 Material 3 的标准 attr**：`?attr/colorSurface`、`colorSurfaceContainerHigh`、`colorPrimary`、`colorPrimaryContainer`、`colorOnSurfaceVariant`、`colorOutlineVariant`、`colorError`… | ① 皮肤切换只需给这些 attr 赋值；② **API 31+ 的壁纸取色（`DynamicColors`）直接生效，不用另写一套**；③ 与 `MaterialCardView`/`Chip`/`SliceSlider` 默认行为一致 |
| 形状 | 自定义 attr：`?attr/ui_shapeCard` / `ui_shapeField` / `ui_shapeButton` / `ui_shapeKey` / `ui_shapeSheet` | M3 的 shape 是 Compose 侧概念，View 侧没有可继承的 attr |
| 尺寸 | 自定义 attr：`?attr/ui_gutter` / `ui_rowPadV` / `ui_swatch` / `ui_topbarH` / `ui_railW` … | 同上；平板覆盖仍在 `values-sw600dp`，不写 `if (width > 600)` |
| 字阶 | 自定义 attr：`ui_textTitleSize` / `ui_textSectionSize` / `ui_textNameSize` / `ui_textMetaSize` / `ui_textValueSize` + `ui_fontTitle`（`sans-serif` vs `serif`） | style 不能被 attr 切换，但 size/family 可以；这样 B 的「大字号」也只是换值 |
| 能力位 | 自定义 bool attr：`ui_cap_stageHeader` / `ui_cap_floatingDock` / `ui_cap_segmented` | 结构差异走这里，页面读 bool 决定用哪个组件（唯一允许的分支点） |

新增依赖（仅一条）：

```toml
material = { module = "com.google.android.material:material", version = "1.13.0" }
```

用它的哪些东西：`MaterialCardView`、`Chip`/`ChipGroup`、`MaterialButton`、`Slider`、`TextInputLayout`、
`BottomSheetDialog`、`DynamicColors`。**不用 Compose**（`EditorUi.kt` 已写明原因：预览要复用输入法渲染器）。

### 3.3 皮肤 = 一个 ThemeOverlay + 若干能力位

```kotlin
enum class Skin(
    val id: String,
    val labelRes: Int,
    @StyleRes val overlay: Int,
    val caps: Caps,
) {
    PROOF("proof", R.string.skin_proof, R.style.Theme_TrimeEditor_Skin_Proof, Caps.NONE),
    TONAL("m3-tonal", R.string.skin_tonal, R.style.Theme_TrimeEditor_Skin_Tonal, Caps.TONAL),
    EXPRESSIVE("m3-expressive", R.string.skin_expressive, R.style.Theme_TrimeEditor_Skin_Expressive,
        Caps(stageHeader = true, floatingDock = true, segmented = true)),
}
```

`Caps` 只是把「结构差异」集中成一处可测数据；页面里的写法是
`if (tokens.caps.stageHeader) StageHeader(...) else TopBar(...)`，全工程只允许这一类分支。

### 3.4 切换机制（谁存、何时生效、生效后干什么）

```
外观页点击 → SkinPrefs.set(id) → 当前 Activity recreate() → 栈里其它 Activity 在 onResume 自检
                                  → 发现 id 变了就地 recreate（栈深 ≤ 3，代价可接受）
```

三条实现细节，每条都踩过坑的类型：

1. **用 `theme.applyStyle(overlay, true)`，不用 `setTheme()`。**
   `setTheme` 会覆盖清单里给 3 个快捷入口挂的 `Theme.TrimeEditor.QuickAction`（半透明、不画界面），
   表现是快捷入口闪一下白屏。`applyStyle` 是在既有主题上叠一层，两个需求都满足。
   调用点：基类 `onCreate` 的**第一行**（`super.onCreate` 之前）。
2. **切换后必须 `recreate()`，不能只 `invalidate()`。**
   与 03 期「改了方案集合要 `reloadTheme()`」同一类错误：View 树里存的是解析后的颜色，重新解析才有意义。
3. **深色/壁纸取色也要 recreate。** `DynamicColors` 影响的是 attr，attr 在 inflate 时被读走，
   运行中改 `uiMode` 时如果 Activity 没重建，会出现「一半新一半旧」。

持久化放在既有 `EditorPrefs`（同一份 SharedPreferences，不新开文件）：

```kotlin
var skin: String   // key = "skin_id"，默认 "m3-tonal"；读到未知值回退默认（不抛异常）
```

### 3.5 壁纸动态取色：v1 不开，留好接口

| 项 | 决定 | 理由 |
|---|---|---|
| v1 | 用**固定种子**：`m3-tonal` 紫 `#6750A4`、`m3-expressive` 暖陶土 `#9C4423`；`proof` 不参与取色 | 编辑器是"改文件的工具"，皮肤每次跟壁纸变会让「我上次看到的那套配色」消失，不利于识别与肌肉记忆 |
| 接口 | 因为颜色走 M3 attr，将来开启只需在 `EditorApplication` 里对 `m3-*` 皮肤调 `DynamicColors.applyToActivitiesIfAvailable`，页面零改动 | 这就是 §3.2 那个选择换来的好处 |
| 低版本 | `minSdk 21`：动态取色只在 API 31+ 生效，21–30 永远走种子 | 不引入 `material-color-utilities` 的 HCT 手算，先不做 |

---

## 4. 打底方案 A（`m3-tonal`）的核心范围

### 4.1 页面清单（9 页 + 4 个外围）

| # | 页面 | 关键实体 | 改造要点 |
|---|---|---|---|
| 1 | 工作台 `MainActivity` | 主题目录 + 主题卡列表 | 卡片化（tonal 卡 + 色带缩略 + 当前标记），路径条 → 卡内副标题 |
| 2 | 键盘列表 `KeyboardListActivity` | 方案 chips + 键盘预览 + 静态检查提示 | chips 改 M3 filter chip；预览板保持不动 |
| 3 | 键盘编辑 `KeyboardEditorActivity` | 画布 + 字段 + 底部六动作 | 画布换卡片容器；宽度滑杆换 M3 Slider；动作条换 `ui_actionBar` |
| 4 | 配色 `ColorEditorActivity` | 54 色项 / 8 组 + 方案增删改 | 分组卡片、色块描边走 token、hex 等宽列宽走 `ui_hexW` |
| 5 | 样式 `StyleEditorActivity` | `PreferenceFragment` 60+ 项 | 只换 attr（见风险 R2），行布局 `pref_proof_*` 保留 |
| 6 | 工具条 `ToolBarActivity` | 示意条 + 顺序 + 二态 | 主按钮/锚点块用 token 形状 |
| 7 | 候选窗 `CandidateWindowActivity` | 两段表单 | 同 5 |
| 8 | 预设键 `PresetKeyListActivity` | 列表 + 编辑对话框 | 对话框改 M3 圆角与按钮层级 |
| 9 | 诊断 `DiagnosticsActivity` | 检查结果列表 + 跳转 | 结果行状态色（warn/stale）走 `colorError` / `colorTertiaryContainer` |
| 附 | 预览 `ThemePreviewActivity`、首启引导、崩溃/冲突对话框、空态 | — | 对话框与横幅统一到 `ui_chrome` 组件 |

### 4.2 组件清单（`ui/kit/`，一处实现、九页受益）

`SkinTopBar`（普通 / 大标题）、`SkinCard`、`SkinListRow`（单行 / 双行 / 带尾随）、`SkinSectionHeader`、
`SkinField`（outlined / 带单位 / 带加减）、`SkinActionBar`、`SkinChipRow`、`SkinBanner`、`SkinDialog`、
`SkinEmpty`、`SkinFab`、`SkinSwatchRow`（色块 + 名称 + key + hex，配色页与预览共用）。

### 4.3 明确不做（写下来防止范围蔓延）

- 不改任何写盘行为、菜单结构、页面路由、快捷入口语义。
- 不做 `m3-expressive` 的「配色页一组一屏」分页（见 §5.2）。
- 不做 Compose、不做动画系统、不做图标重绘（沿用 `cmd_*` 图标库，尺寸走 token）。
- 不做壁纸取色（§3.5）、不做自定义字体打包（沿用系统族）。

---

## 5. B（`m3-expressive`）的接入方式

### 5.1 差异全部落在 token 与能力位（预期：页面代码改动为 0 行）

| 差异 | `m3-tonal` | `m3-expressive` | 落点 |
|---|---|---|---|
| 主色 | 紫 `#6750A4` | 暖陶土 `#9C4423`，青作补色 | 颜色 attr |
| 圆角 | 卡片 16 / 字段 12 / 按钮 20 | 28 / 26 / 999 | `ui_shape*` |
| 字号 | 21 / 12.5 / 10 | 34 / 15 / 11 | `ui_text*Size` |
| 顶栏 | 单行标题 | 色块舞台（36/40 圆角收边） | `ui_cap_stageHeader` |
| 工具条 | 底部固定条 56dp | 悬浮胶囊 dock | `ui_cap_floatingDock` |
| 分组导航 | 分节标题 | 胶囊段控 | `ui_cap_segmented` |

### 5.2 一处**建议砍掉**的差异

效果图里 B 的配色页是「一组一屏 + 大色块」。它能出图，但不该进代码：

- 它改变的是**操作路径**（找第 47 个色值要翻 6 次），不是观感；
- 同一页面两份交互 = 两套回归面（翻页状态、返回栈、组内滚动位置）；
- 与"编辑器要快"直接冲突。

建议 B 只做「表皮」（形状 / 字号 / 色 / 舞台头 / 悬浮工具条），配色页仍是一张长表，靠形状与字号体现差异。
如果你坚持要分页，我建议把它做成**独立开关**（`外观 → 配色页分页`），而不是绑在 B 上——这样 A 也能用，
且不用为"皮肤"和"布局"两件事做笛卡尔积。**这一条需要你拍板**（见 §10 待决项）。

### 5.3 接入顺序

B **必须**排在 A 全部页面换装之后：B 的舞台头与悬浮 dock 是 `SkinTopBar`/`SkinActionBar` 的两个变体，
A 不先把这两个组件的接口定稳，B 就是在改一个还在动的组件。

---

## 6. 阶段拆分与交付物

> 每阶段的"退出条件"都是**可复现的命令或可数的截图**，不写"完成改造"这种话。

### P0 地基：把管道铺通，画面不动

| 项 | 内容 |
|---|---|
| 做 | ① 引 `material` 依赖，主题改为「Base + SkinOverlay」结构；② 新建 `EditorActivity` 基类，13 个 Activity 改继承（快捷入口 3 个重写 `baseTheme`）；③ `attrs.xml`（颜色用 M3 标准名，形状/尺寸/字阶/能力位用 `ui_` 前缀）；④ `Skin` / `SkinPrefs` / `UiTokens` / `SkinSwitch`；⑤ `proof` 皮肤映射（把现有 `proof_*` 值填进 M3 attr + `ui_*` attr）；⑥ `grep` 闸门脚本：`ui/` 下禁止出现 `R.color.` 与十六进制色值 |
| 交付 | 可运行的 app，`proof` 皮肤下界面与改造前**逐像素一致**；`m3-tonal` 皮肤下页面能起（允许难看） |
| 退出 | ① 四道门禁绿；② 9 页 × 2 主题（亮/暗）截图与改造前用 `.tmp/pngdiff` 比对，**差异像素为 0**；③ 单测新增：皮肤持久化默认值与非法值回退、`Skin.caps` 与 overlay 一一对应、`UiTokens` 对缺失 attr 的兜底 |

### P1 外壳组件：A 的骨架

| 项 | 内容 |
|---|---|
| 做 | `ui/kit/` 12 个组件 + `ui/chrome`（对话框/横幅/空态）；9 页把容器换成新组件，**内容与字段一律不动** |
| 交付 | A 皮肤下 9 页结构统一（顶栏、卡片、分节、动作条），内容仍由旧代码填充 |
| 退出 | 9 页进出各 20 轮，`mCurrentFocus` 逐页核对，0 崩溃；滚动位置、选中项、展开态在旋转后不丢 |

### P2 工作台 + 键盘列表

| 项 | 内容 |
|---|---|
| 做 | 主题卡（名称 / 文件名 / 色带 / 当前标记 / 溢出菜单）、路径卡、空态与无权限态；键盘 chips + 静态检查提示条 |
| 交付 | 效果图 A1、A2 的上半屏 |
| 退出 | 4 个主题、0 个主题、无权限三种状态各有截图；长按菜单项与改造前逐项一致（12 项） |

### P3 键盘编辑（最重的一期）

| 项 | 内容 |
|---|---|
| 做 | 画布卡片 + 选中键描边（token 色）、字段组、M3 Slider 宽度控件、底部六动作条、撤销/重做禁用态 |
| 交付 | 效果图 A3 |
| 退出 | ① 50 个布局打开→选键→改宽→撤销→返回，无状态错乱；② 画布与输入法预览的像素闸门（03 期 D2 那套）重跑通过；③ 底部动作条 6 项在 411dp / 848dp 下都不换行、热区 ≥48dp |

### P4 配色 + 样式

| 项 | 内容 |
|---|---|
| 做 | 54 色项的分组卡（8 组）、色块描边、hex 列宽、方案增删改三动作改卡内行；`Preference` 页只换 attr |
| 交付 | 效果图 A2 的下半屏 |
| 退出 | ① `theme-core` 的逐字节闸门用例全绿（**一条都不许改**）；② 新建→删除往返后文件逐字节还原；③ 54 项滚动到底的首帧时间 ≤ 改造前 +10% |

### P5 工具条 / 候选窗 / 预设键 / 预览

| 项 | 内容 |
|---|---|
| 做 | 三段表单、二态主按钮、锚点块、动作列表与编辑对话框、预览页外壳 |
| 交付 | 4 页 A 皮肤完成 |
| 退出 | 每页"改一项 → 只动一行"的闸门用例复跑；对话框在暗色下无低对比文字 |

### P6 诊断 / 引导 / 弹窗 / 空态 / 深色与平板

| 项 | 内容 |
|---|---|
| 做 | 诊断结果行状态色、跳转链接、三步引导、崩溃与冲突对话框、空态插画（纯几何，不引图）、`values-night` 全量校对、`values-sw600dp` 校对 |
| 交付 | A 皮肤全页覆盖（可发 `0.3.0-alpha`） |
| 退出 | ① 亮/暗 × 手机/平板 = 4 组 × 9 页截图齐；② 正文对比度 ≥4.5:1、大字 ≥3:1（脚本抽查所有 token 组合）；③ 字号 1.3 倍时无截断 |

### P7 接入 B

| 项 | 内容 |
|---|---|
| 做 | `Theme.TrimeEditor.Skin.Expressive` 一张 overlay + 三个能力位组件变体（舞台头 / 悬浮 dock / 胶囊段控）+ 外观页的三张预览卡 |
| 交付 | `0.3.0-beta`，设置里可切换三套皮肤 |
| 退出 | ① 切换后 9 页全部正常（不要求每页都重新截图，但四类页面必查：列表 / 长表 / 画布 / 表单）；② 切换耗时 ≤ 400ms（recreate 实测）；③ 页面代码 diff 里**不出现** `EXPRESSIVE` 字样 |

### P8 打磨与验收

| 项 | 内容 |
|---|---|
| 做 | 对比度、触摸目标、减少动态效果、TalkBack 标签、冷启动与滚动性能、真机双设备走查、截图归档、`0.3.0` 打包 |
| 交付 | `dist/同文编辑器-0.3.0.apk` + 验收报告（截图清单 + 门禁输出） |
| 退出 | §9 全部条目逐条勾掉 |

---

## 7. 依赖与里程碑

```
P0 ──► P1 ──► P2 ──► P3 ──► P4 ──► P5 ──► P6 ──► P7 ──► P8
 │      │                    │                    │
 └ M1   └ 组件冻结            └ M2 主路径          └ M3 可切换
```

| 里程碑 | 含义 | 判定 |
|---|---|---|
| **M1 管道就绪** | 切皮肤这件事成立，且不伤旧观感 | P0 退出条件全绿（含像素 0 差异） |
| **M2 主路径可用** | 工作台 → 键盘 → 画布 → 配色 这条主链在 A 下完整 | P2–P4 退出条件全绿 |
| **M3 全页覆盖 + B 可切换** | 9 页都在 A 下，B 能一键切 | P5–P7 退出条件全绿 |
| **M4 可发布** | 对比度/性能/真机验收 | P8 退出条件全绿 |

外部依赖（不解决就无法开工的）：

| 依赖 | 现状 | 处理 |
|---|---|---|
| `com.google.android.material:1.13.0` | 未引入 | P0 第一件事；若与 AGP 9.4.1 冲突，退到 1.12.0（M3 组件与 `DynamicColors` 都在） |
| JDK / `GRADLE_USER_HOME` | 已配（工区内 `.gradle-user-home`） | 沿用，不改 |
| 设备 | 平板 `J7JBXCLVPFTSQKFA`（可 `input tap`）、手机 `5e551466`（`input tap` 无效） | 手机验收一律 `uiautomator dump` 读 bounds，不点坐标 |
| `proof` 皮肤基线截图 | 需要先补（现在 `.tmp/` 里是零散的） | P0 开工第一件事：把 9 页 × 亮暗 × 双设备存成基线 |

---

## 8. 风险与应对

| # | 风险 | 触发概率 | 影响 | 应对 |
|---|---|---|---|---|
| R1 | **撞名**：文档/代码里"方案 A"既指旧校样又指新打底 | 高 | 计划读错、改错皮肤 | 全文用 skin id；旧文档里的「A 方案」保留原文但加注「= skin `proof`」 |
| R2 | `Theme.Material3` + `PreferenceFragmentCompat` 不兼容（缺 `preferenceTheme`、`SwitchPreferenceCompat` 取不到 attr） | 中 | 样式/候选窗两页白屏 | P0 先做一个**探针页**（一个 Preference 屏）验证；不通过则保留 `Theme.AppCompat` 作 Base，皮肤只叠 attr（M3 组件仍可用） |
| R3 | 269 处 `proof_*` 引用改不干净，出现"一半新一半旧" | 高 | 观感碎裂 | ① 页面层禁止直接引用，全部经 `UiTokens`；② P0 加 `grep` 闸门：`ui/` 与 `*Activity.kt` 里出现 `R.color.proof_` 即失败；③ 逐页改、逐页截 |
| R4 | 像素基线失效（字体、状态栏、时钟） | 高 | "0 差异"闸门变成假绿 | 基线固定：状态栏时间冻结、隐藏电池、`force-device-scale-factor=1`、同一设备同一亮度；只比内容区 |
| R5 | 键盘预览被顺手改动，与输入法不一致 | 中 | 编辑器最核心的可信度没了 | `KeyboardCanvasView`/`ThemeBoard`/`KeyFaceRenderer` 划为禁区，P3 只换外层容器；D2 像素闸门重跑 |
| R6 | 暖色在深色下"发脏"（B 的已知问题） | 高 | B 观感崩 | 暗色单独调一轮（不复用亮色反相），accent 降饱和；P7 退出条件加"暗色四类页面必查" |
| R7 | `recreate()` 让用户丢编辑中的草稿 | 中 | 数据丢失投诉 | 皮肤切换入口只在**外观页**（不在编辑页里），切完 `finish()` 回上一级；编辑页不提供换肤入口 |
| R8 | 平板两栏判定被写进页面 | 中 | 又变成 9 处 `if` | 沿用 `proof_two_pane` 的做法，改名为 `ui_two_pane`（皮肤无关），仍只有一处 |
| R9 | Material3 组件默认高度/内边距与设计稿不符（如 Chip 32dp vs 稿子 30dp） | 高 | 反复微调耗时 | 以 token 为准，组件只做样式包装不改内部度量；接受 ±2dp 偏差，不为 2dp 改库 |
| R10 | 构建时间继续膨胀（新增依赖 + 用例） | 中 | 迭代变慢 | 门禁仍拆两次；新增用例全部是纯 JVM（不引 Robolectric） |
| R11 | 诊断页/引导页被漏掉（它们不在主链上） | 中 | 上线后一眼假 | P6 单独一期，退出条件按"9 页"计数，缺一页不算过 |
| R12 | 范围蔓延到 `theme-core`（"这个字段 UI 上要支持 X"） | 中 | 触碰逐字节闸门 | 计划里写明数据层冻结；确需改，另开一期并重跑全套 golden |

---

## 9. 验收标准

### 9.1 视觉

- 9 页 × 2 皮肤 × 2 主题（亮/暗）× 2 设备（411dp / 848dp）= **72 张截图**，全部归档到 `doc/design/shots/<skin>/`。
- 正文文字对比度 ≥ 4.5:1，大字 ≥ 3:1；`colorOutlineVariant` 分隔线在两种主题下都可见（≥1.5:1）。
- `proof` 皮肤下与改造前逐像素一致（P0 闸门）；`m3-tonal` 下与效果图 A 的三屏结构一致（允许 2dp 级偏差）。
- 白色/透明色块必须带描边（沿用 03 期规则：没有描边，白放在白上就"消失"）。

### 9.2 行为

- 每页 进出 50 轮、每轮核对 `mCurrentFocus`，0 MISS / 0 崩溃 / 0 ANR（沿用 D8 的脚本与判定）。
- 13 个 Activity（含 3 个快捷入口）全部能起；快捷入口不闪白屏、不画界面。
- 切换皮肤后：栈内页面全部换装；返回栈深度不变；不丢 `EditorPrefs` 里的目录/方案/主题选择。

### 9.3 数据安全（最高优先级）

- `theme-core` diff 为 **0 行**。
- 全部 golden 用例保持：写回后除目标行外逐字节不变、create→delete 逐字节 no-op、`.revision` 四重校验。
- 54 色项在 A/B 两皮肤下的编辑结果写入同一份文件，内容一致（皮肤不影响数据）。

### 9.4 性能

- 冷启动 ≤ 改造前 +150ms（基线 639/443/417ms）。
- 列表滚动掉帧 ≤ 5%（`gfxinfo` 对比），54 项首帧 ≤ +10%。
- 皮肤切换（recreate）≤ 400ms。

### 9.5 无障碍与适配

- 所有可点区域 ≥ 48dp × 48dp（视觉可小，热区不可小）。
- 系统字号 1.3 倍：无截断、无重叠，长表仍可滚到底。
- TalkBack：顶栏按钮、色块行（读出"候选文字，当前 #FFFFFF"）、动作条六项都有可用标签。
- 减少动态效果开启时，无位移/缩放动画。

### 9.6 工程门禁

```
./gradlew spotlessKotlinCheck            # 根工程
./gradlew testDebugUnitTest              # 全模块单测
./gradlew lintDebug                      # 0 error
./gradlew assembleDebug                  # 产物在 app/build/outputs/apk/debug/
./gradlew distApk                        # 发布包进 dist/
```

---

## 10. 推进顺序（可直接照做）

**第 0 步｜基线（半天）**
1. 补基线截图：9 页 × 亮暗 × 双设备，存 `doc/design/shots/proof-baseline/`。
2. 写 `.tmp/pngdiff` 的 skin 参数化版本（`--skin` 决定基线目录）。

**第 1 步｜P0 管道（2–3 天）**
3. `libs.versions.toml` 加 `material`；`app/build.gradle.kts` 与 `editor-ui` 各加依赖。
4. `values/attrs.xml`：M3 颜色名（不重定义，直接用库的）+ `ui_*` 形状/尺寸/字阶/能力位。
5. `Theme.TrimeEditor.Base` + `Skin.Proof/Tonal/Expressive` 三个 overlay（先只填 color，形状尺寸用现有值）。
6. `ui/skin/Skin.kt`、`SkinPrefs.kt`（进 `EditorPrefs`）、`UiTokens.kt`、`SkinSwitch.kt`。
7. `EditorActivity`（`applyStyle` + insets + 返回键），13 个 Activity 改继承。
8. 跑 §9.6 四道门禁 + 像素 0 差异闸门。

**第 2 步｜P1 组件冻结（3–4 天）**
9. `ui/kit/` 12 个组件，先只服务工作台一页；用起来顺手再复制到其余 8 页。
10. 组件接口冻结（此后 B 只加变体，不改签名）。

**第 3 步｜P2→P6 逐页换装（按页推进，每页一个提交）**
11. 顺序：工作台 → 键盘列表 → 键盘编辑 → 配色 → 样式 → 工具条 → 候选窗 → 预设键 → 诊断/引导。
12. 每页提交必须带：该页截图（亮/暗）+ 该页的进出脚本结果。

**第 4 步｜P7 接入 B（2–3 天）**
13. 只写 overlay + 3 个能力位变体；写完后 `git diff` 检查页面文件应为 0 行改动（若不为 0，说明 P1 的组件接口没做对，回头改组件而不是改页面）。

**第 5 步｜P8 打磨（3 天）**
14. 对比度脚本、字号 1.3、TalkBack、双设备走查、`distApk`。

**待你拍板的两件事**（不影响 P0 开工）：
- **B 的配色页要不要"一组一屏"**（建议不要，见 §5.2）；
- **`proof` 皮肤留多久**（建议留到 `0.3.0` 发布后两周，再决定删或留）。

---

## 11. 实施结果（2026-11-07）

已按本计划从 P0 推进到 P8，实际产出与偏差记录在 **`doc/ui-skin-implementation.md`**。
与计划不一致的六处，按影响排序：

1. **没有引入 `com.google.android.material`**（计划 §P0 第一件事）。M3 观感由 token 实现，
   零新增依赖；代价是 ripple / state layer 仍是 AppCompat 的。
2. **P1 的 `ui/kit` 12 个组件改为「token 工厂 + 基类 chrome」**。9 个页面已有 29 个调好的布局，
   重写组件等于把换皮肤变成重写界面。代价：结构差异靠视图树识别，不是显式契约。
3. **B 的「配色页一组一屏」按 §5.2 的建议砍掉**，B 只做表皮。
4. **画布不加卡片**：预览必须与输入法同宽。
5. **动态取色**仍未做（v1 范围外），且因为颜色走自定义 attr，接 `DynamicColors` 需要先换取值渠道。
6. **像素基线闸门未建立**（仓库没有改造前基线截图），P0 的「0 差异」改为人工对照 + 保留
   `proof` 皮肤可回退。这是本轮最大的验收缺口，建议下一轮第一件事补上。

门禁结果：`spotlessKotlinCheck` / `test`（406 例）/ `lintDebug`（0 error）/ `assembleDebug` /
`distApk` 全绿；真机 8 页拉起 0 崩溃；`theme-core` 零改动。

---

## 12. 2026-11-07 后续：改为「B 优先」

按设计稿重做结构、并以方向 B 为主实现的流程，见 **`doc/ui-expressive-plan.md`**。
本文的皮肤管道（token / overlay / 切换）继续有效，但**页面结构以 B 为准**，
`proof` / `m3-tonal` 的结构不再单独维护（见 B 计划 §4.5）。
