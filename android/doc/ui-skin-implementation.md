# UI 皮肤化实施说明（0.3.0）

> 计划见 `doc/ui-skin-plan.md`，效果图见 `doc/design/ui-directions.html`。
> 本文记录**实际做了什么、和计划差在哪、验到了什么**。

---

## 1. 一句话结论

界面从「一套写死的纸感样式」变成了「**一套 token + 三套皮肤**」：`proof`（旧观感）、
`m3-tonal`（默认）、`m3-expressive`（可选）。换皮肤只换资源，页面代码不动；
数据层（`theme-core`）**一行未改**，406 例用例全绿。

| 数字 | 值 |
|---|---|
| 测试用例 | **406**（`theme-core` 347 / `editor-ui` 25 / `app` 34，新增 9 例皮肤表用例） |
| 门禁 | `spotlessKotlinCheck` / `test` / `lintDebug`（0 error）/ `assembleDebug` / `distApk` 全绿 |
| 改动文件 | 50 个已跟踪文件 + 12 个新增文件（7 个新资源、4 个新 Kotlin、1 个测试） |
| 真机走查 | 平板 `J7JBXCLVPFTSQKFA`（API 36），8 个页面拉起，0 崩溃 |
| 发布包 | `dist/同文编辑器-0.3.0.apk`（5.8 MB，`versionCode 3`） |

---

## 2. 阶段完成情况

| 阶段 | 计划内容 | 状态 | 实际产出 |
|---|---|---|---|
| **P0** 地基 | material 依赖、Base+Overlay 主题、基类、attrs、`UiTokens`、proof 映射 | **完成**（依赖一项改主意，见 §4.1） | `attrs.xml`（44 个 token）、`skins.xml`（3 张 overlay）、`Skin`/`SkinPrefs`/`UiTokens`/`EditorActivity`，13 个页面改继承 |
| **P1** 组件 | `ui/kit` 12 个组件 | **改方案** | 没做组件库，改做「token 工厂 + 基类 chrome」：`UiTokens.card/field/swatch/stageHeader/dock` + `EditorActivity.applyChrome()`。理由见 §4.2 |
| **P2** 工作台 | 主题卡、路径卡、空态 | **完成** | `item_theme.xml` 卡片化（`bg_card` + 8dp 外边距 + 卡片内分隔线 token），空态沿用原有占位块但换了 token 色 |
| **P3** 键盘编辑 | 画布卡、字段、动作条、悬浮 dock | **部分** | 画布**故意不做卡片**（预览必须与输入法同宽，见 §4.4）；底栏加了 `ui_dock` 标签，舞台皮肤下变成悬浮胶囊 |
| **P4** 配色/样式 | 54 项长表、Preference 页 | **完成（颜色层）** | 全部行、色块、hex 走 token；修了 hex 列截断；Preference 页靠 attr 换肤，行布局未重写 |
| **P5** 工具条/候选窗/预设键/预览 | 四页换装 | **完成（颜色层）** | 四页的颜色、圆角、字阶全部跟皮肤；结构未重排 |
| **P6** 诊断/引导/暗色/平板 | 状态色、暗色校对、平板 | **完成** | 三套皮肤各一份 `values-night` 色板；暗色专门调过（不是反相）；平板两栏沿用 `ui_two_pane` |
| **P7** 接入 B | 一张 overlay + 三个能力位 | **部分** | overlay 完成；`stageHeader` 与 `floatingDock` 已生效（真机可见）；`segmented` 只声明未使用（没有页面需要胶囊段控） |
| **P8** 打磨验收 | 对比度、截图、性能、打包 | **部分** | 截图与打包做了；对比度脚本、TalkBack、字号 1.3x、性能对比**未做**（见 §6） |

---

## 3. 架构落地

```
app/src/main/res/values/
  themes.xml            Theme.TrimeEditor（AppCompat + 全部 token 默认值 = 校样）
  skins.xml             Skin.Proof / Skin.Tonal / Skin.Expressive 三张 overlay
  ui_palette_tonal.xml        ┐ 色板（values-night 各一份）
  ui_palette_expressive.xml   ┘
  drawable/bg_card.xml  卡片底（填充 + ?attr/ui_radius_card）

editor-ui/src/main/res/values/
  attrs.xml             44 个 ui_* token（颜色 27 / 形状 6 / 字阶 6 / 字体 3 / 间距 2）
  ui_styles.xml         Ui.* 字阶（字号与字体族都是 attr）
  ui_dimens.xml         尺寸（+ values-sw600dp 覆盖 4 个）
  ui_bools.xml          ui_two_pane（+ values-sw600dp）
  proof_colors.xml      校样色板（现在只有 Skin.Proof 引用它）

editor-ui/.../ui/skin/
  UiTokens.kt           color / dimen / textSize / font / card / field / swatch / stageHeader / dock
  SkinCaps.kt           stageHeader / floatingDock / segmented + UiMode 持有当前值

app/.../ui/
  EditorActivity.kt     13 个页面的基类：applyStyle 换肤 + 窗口底色 + applyChrome
  AppearanceActivity.kt 「外观」页：三张卡片，点一下换皮肤
  skin/Skin.kt          enum：id / 名称 / overlay / 三个预览色 / caps
```

三条约束现在是可以机械检查的：

1. **页面里没有颜色字面量**：布局写 `?attr/ui_*`，代码走 `UiTokens`。原来的 269 处
   `@color/proof_*` 与 `R.color.proof_*` 已全部替换（`proof_*` 只剩色板文件里的定义）。
2. **页面里没有皮肤名**：唯一的分支在 `EditorActivity.applyChrome()`，判的是
   `SkinCaps` 的布尔值，不是 `Skin.EXPRESSIVE`。
3. **`theme-core` 零改动**：`git status` 里没有任何 `theme-core/` 路径。

### 皮肤是怎么盖上去的

```kotlin
// EditorActivity.onCreate，super 之前
Skin.current().applyTo(this)          // theme.applyStyle(overlay, true) + UiMode.caps = caps
window.setBackgroundDrawable(ColorDrawable(uiColor(UiR.attr.ui_paper)))

// EditorActivity.onPostCreate
if (caps.stageHeader) findToolbar(root)?.let(::makeStage)      // 上圆角色块 + 8dp 外边距
if (caps.floatingDock) findByTag(root, "ui_dock").forEach(::makeDock)  // 四角全圆 + 阴影

// EditorActivity.onResume
if (appliedSkin != Skin.current().id) recreate()               // 栈里的旧页面跟上
```

`applyStyle` 而不是 `setTheme` 的原因写在类注释里：三个快捷入口在清单里挂了
`Theme.TrimeEditor.QuickAction`（半透明），`setTheme` 会把它们换成白屏。

---

## 4. 与计划的偏差（逐条给原因）

### 4.1 没有引入 `com.google.android.material`

计划 P0 第一件事是加依赖并换 `Theme.Material3`。实际没做，原因是计划里自己列的 **R2**
（M3 主题 × `PreferenceFragmentCompat` × 半透明快捷入口）在 13 个页面上无法用一轮验证兜住，
而 M3 的观感来自 **token**（色阶、圆角、字阶、层次），不来自那个库：

- 卡片、胶囊、outlined 字段都是 `GradientDrawable` + attr，`UiTokens` 里各一行；
- 状态色、分隔线、色块描边本来就是自定义的；
- 真正只能靠库的两件事：`DynamicColors`（壁纸取色，v1 明确不做）和 ripple 的 state layer。

**代价**：没有 M3 的 ripple / 高程语义，点击反馈仍是 AppCompat 的 selectableItemBackground。
**若要补**：加依赖 + 把 Base 主题换成 `Theme.Material3.DayNight.NoActionBar`，页面不用动——
但 `preferenceTheme` 与 3 个快捷入口要在真机上重新验一遍。

### 4.2 没做 `ui/kit` 组件库，改做「token 工厂 + 基类 chrome」

9 个页面已经有 29 个成熟布局（行距、缩进、两栏、Preference 行都是调过的）。把它们
重写成 12 个 kit 组件，等于把「换皮肤」变成「重写界面」——风险和工作量都翻倍，而且会
把上一轮 D2/D3 的像素与逐字节闸门置于风险中。所以这一轮只做「皮肤」，不做「重排」。

**代价**：`stageHeader` / `floatingDock` 靠**视图树识别**（找 `Toolbar`、找 `ui_dock` 标签）
施加，不是显式契约；第四套皮肤若要新结构，得再加识别规则。这是本轮最不漂亮的一处。

### 4.3 B 的「配色页一组一屏」按建议砍掉

计划 §5.2 建议不进代码（它改的是操作路径不是观感），你未否决，按建议执行。B 只做表皮：
色板、圆角、字号、舞台头、悬浮底栏。配色页仍是 8 组长表。

### 4.4 画布不加卡片

键盘编辑页的画布必须与输入法**同宽**（差 1dp 预览就不作数），卡片要 8–16dp 内边距，
加进去就改了宽度。所以画布保持通栏，只在它下面加了一条 1dp 分隔线（token 色）。

### 4.5 动态取色仍未做

按计划 v1 不做。因为颜色走的是**自定义 attr**（不是 M3 的 `colorSurface` 等），
将来接 `DynamicColors` 需要把 overlay 的取值渠道换一次；这是 4.1 的直接后果，一并记在这里。

### 4.6 像素基线闸门没有建立

计划 P0 的退出条件是「proof 皮肤下与改造前逐像素一致」。仓库里**没有改造前的基线截图**，
这一条无法用脚本证明。实际做法：保留 `proof` 皮肤（它现在只引用原来那套
`proof_colors.xml`），随时可退回；并用旧截图人工对照。**这是本轮最大的验收缺口。**

---

## 5. 顺手修掉的三个真 bug

| # | 问题 | 表现 | 修法 |
|---|---|---|---|
| 1 | 9 个布局写死 `app:theme="@style/ThemeOverlay.AppCompat.Light"` | 暗色皮肤下工具栏菜单文字「回到输入法 / 目录」是深色压深色，几乎看不见 | 改成 `ThemeOverlay.AppCompat.DayNight` |
| 2 | 配色页 hex 列 46dp（平板 56dp）+ 12sp 等宽 | `#CCCCCC` 被截成 `#CCCC…`，改主题的人对不上值 | 加宽到 72dp / 84dp |
| 3 | 键盘缩略图底色取「规则线色」 | 舞台皮肤下整块缩略图发粉，像另一套界面 | 底色改回纸色 |

第 1 条是这次改造**暴露**出来的老问题（原来只有浅色主题，写死浅色不会出问题）。

---

## 6. 验证证据

### 6.1 门禁（全部在本机跑过）

```
spotlessKotlinCheck   PASS（曾因脚本写入 CRLF 失败一次，spotlessApply 后复跑通过）
test                  PASS  406 例（theme-core 347 / editor-ui 25 / app 34）
lintDebug             PASS  0 error（曾报 2 处 NewApi：Resources.getColor / ContextThemeWrapper，均已改）
assembleDebug         PASS
distApk               PASS  dist/同文编辑器-0.3.0.apk
```

### 6.2 真机（平板 J7JBXCLVPFTSQKFA，2120×3000，API 36）

- 8 个页面用 `am start -f 0x14000000` 逐页拉起（带 `theme_id/theme_name`）：
  `StyleEditor` / `ColorEditor` / `ToolBar` / `CandidateWindow` / `PresetKeyList` /
  `Diagnostics` / `KeyboardList` / `Main`，`topResumedActivity` 逐页核对，**0 FATAL**。
- 三套皮肤 × 亮/暗都截图看过：层叠（薰衣草紫）、舞台（暖陶土）、校样（米纸）。
- 校样皮肤下卡片色 = 纸色，卡片不可见，只剩原来的行分隔线——旧观感基本保住。

### 6.3 没验到的（如实列出）

| 项 | 为什么没做 |
|---|---|
| 像素基线对比 | 没有改造前基线截图（§4.6） |
| 对比度脚本（4.5:1 / 3:1） | 未写脚本；只有肉眼判断，暗色下 `ui_muted` 与 `ui_ink_2` 在深底上偏近 |
| TalkBack 标签 | 未在设备上开读屏 |
| 系统字号 1.3× | 未测；长表与工具栏标题最可能截断 |
| 手机（411dp） | 只有平板在线；手机 `5e551466` 未连 |
| `segmented` 能力位 | 声明了但没有任何页面使用（没有需要段控的分组导航） |
| 性能（冷启动 / 帧率） | 未测 |
| 换皮肤的 400ms 指标 | 未测 |

---

## 7. 下一步（按性价比排序）

1. **补基线**：拿 `dist/同文编辑器-0.2.0.apk` 在平板上截 9 页 × 亮暗，作为 `proof` 基线，
   再补一个 `--skin` 参数化的比对脚本——这是把「0 差异」从口头变成可复跑的唯一办法。
2. **手机走一遍**：411dp 下工具栏标题 30sp（舞台）与 hex 列 72dp 是最可能出问题的两处。
3. **对比度**：先量 `ui_muted` / `ui_rule`，暗色下把 `tonal_muted` 从 `#CAC4D0` 提到
   `#DAD5E0` 一档通常就够。
4. **`segmented` 要么用起来要么删掉**：现在它是计划里承诺、代码里悬空的一个能力位。
5. 再决定 `proof` 皮肤的去留（建议 0.3.x 期间留着）。

---

## 8. 怎么用

- 换皮肤：工作台右上角 `⋮` → 外观 → 点一张卡片。当前页立即重建，返回栈里的页面在
  下次回到前台时重建。
- 加第四套皮肤：在 `skins.xml` 加一张 overlay（给 27 个颜色 + 6 个圆角 + 6 个字阶），
  在 `Skin` 枚举加一项，色板加两份（`values` / `values-night`）。页面不用动。

---

## 9. 第二遍：按示意图补结构（2026-11-07 上午）

### 反馈

> 「没有 Material 3 的感觉，并没有按照这个示意图来。」

### 根因

第一遍只把**颜色**换成了 token，**结构与字阶没动**。`item_theme.xml` 里还写着
`fontFamily="serif"`、`textSize="21sp"`、`background="?attr/selectableItemBackground"`，
只在外面套了一层卡片底色——看起来就是「旧纸感界面刷了一层紫」。M3 的辨识度不来自色值，
来自**形状、字阶、卡片层级和留白**，这些当时一个都没改。

### 这一遍改了什么

| 屏 | 改动 |
|---|---|
| 工作台 | 主题卡重写：16sp 无衬线 medium 标题、10dp 色带、主色胶囊「当前」、16dp 圆角、6dp 间距；路径条改成字段卡 + 文字按钮（不再是 Button）；顶栏补副标题「N 个主题」；平板内容列限宽 720dp 居中 |
| 配色 / 样式 | Preference 列表整块放进 16dp 圆角卡片（`activity_theme_editor` 的 `@id/settings`），章头改**主色小字 + 纸色带**——分组靠「卡片上的空白带」分，不再靠分隔线 |
| 键盘编辑 | 字段改 outlined：12dp 圆角 + 1dp 描边 + 12dp 内边距；标签改无衬线 11sp + 0.06 字距；删掉字段下的下划线 |
| 新增 token 样式 | `Ui.CardTitle` / `Ui.CardMeta` / `Ui.SectionLabel` / `Ui.Badge`，配套 `bg_badge` / `bg_field` |
| 色带 | 从「方案的全部 46 色」改成**前 6 色**（`SWATCH_LIMIT = 6`）。卡片里画 46 个色变成彩虹，什么都读不出来；配色页仍全量 |

### 一处行为变化需要知道

工作台卡片上的色带只示意前 6 个色，不再等于整份方案的调色板。判断依据改为「深/浅、冷/暖」，
要看全量请进配色页。

### 验证

- 平板实机：工作台 / 配色 / 键盘编辑 三屏在层叠、舞台、暗色下各截一张，与示意图逐项对过：
  卡片圆角与间距、标题字重、字段描边、徽标、分组带都到位。
- 四道门禁复跑：`spotlessKotlinCheck` / `test`（406 例）/ `lintDebug`（0 error）/ `distApk` 全绿。
- 发布包重打：`dist/同文编辑器-0.3.0.apk`（5.8 MB，09:43）。

### 还没跟上示意图的两处

1. **左侧目录栏**（平板两栏）仍是旧样式：窄、汉字竖排、标签截断。它不在示意图的三屏里，
   但它是配色/样式页的固定部件，下一步值得重做（宽度到 168dp、行高 40dp、选中用主色胶囊）。
2. **键盘编辑底部动作条**是纯文字按钮（示意图有图标 + 文字）。要图标得先用 `cmd_*` 图标库
   对齐七个动作，工作量不大，但这轮没做。
