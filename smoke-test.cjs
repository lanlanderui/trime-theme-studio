const assert = require("node:assert/strict");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const { chromium } = require("playwright");

function cssRgb(value) {
  const channels = String(value).match(/[\d.]+/g)?.slice(0, 3).map(Number);
  assert.equal(channels?.length, 3, `无法解析 CSS 颜色：${value}`);
  return channels;
}

function relativeLuminance(value) {
  const [red, green, blue] = cssRgb(value).map((channel) => {
    const normalized = channel / 255;
    return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function cssContrastRatio(foreground, background) {
  const a = relativeLuminance(foreground);
  const b = relativeLuminance(background);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 920 } });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });

  await page.goto(pathToFileURL(path.resolve(__dirname, "index.html")).href, {
    waitUntil: "domcontentloaded",
  });
  await page.waitForSelector(".theme-card");

  assert.equal(await page.locator("#sourceName").textContent(), "trime.yaml", "应使用用户提供的 trime.yaml 作为初始模板");
  assert.equal(await page.locator(".theme-card").count(), 6, "初始模板应显示默认主题与 5 套新主题");
  const initialLayoutCount = await page.locator("#layoutSelect option").count();
  const initialKeyCount = await page.locator(".trime-key").count();
  const initialColorRowCount = await page.locator(".color-row").count();
  assert.equal(initialLayoutCount, 3, "应读取模板中的 3 个实体键盘布局");
  assert.ok(initialKeyCount >= 30, "应渲染完整键盘");
  assert.ok(initialColorRowCount >= 42, "应渲染官方完整颜色字段");
  assert.equal(await page.locator(".deprecated-badge").count(), 2, "两个 preview_* 旧字段应标记为已废弃");
  assert.equal(await page.locator('[data-color-row="keyboard_background"]').count(), 0, "不应把 keyboard_background 当成官方字段");
  assert.equal(await page.locator('[data-color-row="root_background"] .role-base').count(), 1, "共同底层应有明确标记");
  assert.equal(await page.locator('[data-color-row="keyboard_back_color"] .role-region').count(), 1, "键盘区域层应有明确标记");
  assert.equal(await page.locator("#layerGuide").count(), 1, "应提供颜色层级图解");

  const previewLayers = await page.evaluate(() => ({
    root: getComputedStyle(document.querySelector(".ime")).backgroundColor,
    candidate: getComputedStyle(document.querySelector(".candidate-row")).backgroundColor,
    keyboard: getComputedStyle(document.querySelector(".keyboard")).backgroundColor,
  }));
  assert.equal(previewLayers.root, "rgb(228, 231, 233)", "共同底层应读取 root_background → back_color");
  assert.equal(previewLayers.candidate, "rgb(228, 231, 233)", "候选栏应读取 candidate_background → back_color");
  assert.equal(previewLayers.keyboard, "rgb(255, 255, 255)", "键盘区应读取 keyboard_back_color");
  const themeIds = await page.locator(".theme-card").evaluateAll((cards) => cards.map((card) => card.dataset.themeId));
  const layoutIds = await page.locator("#layoutSelect option").evaluateAll((options) => options.map((option) => option.value));
  assert.deepEqual(themeIds, ["default", "mist_jade", "apricot_cream", "indigo_night", "pine_ink", "sakura_slate"]);

  await page.locator('[data-theme-id="default"]').click();
  assert.equal(await page.locator("#previewThemeId").textContent(), "default");

  const popupBackInput = page.locator('.color-value-input[data-color-key="popup_back_color"]');
  await popupBackInput.fill("0xFFFF0000");
  await popupBackInput.dispatchEvent("change");
  await page.waitForTimeout(50);
  assert.equal(await page.locator("#dirtyBadge").isVisible(), true, "修改后应显示已修改");

  await page.locator("#exportButton").click();
  const downloadPromise = page.waitForEvent("download");
  await page.locator("#downloadFullButton").click();
  const download = await downloadPromise;
  const stream = await download.createReadStream();
  let exported = "";
  for await (const chunk of stream) exported += chunk.toString("utf8");
  assert.match(exported, /  default:[\s\S]*?popup_back_color: 0xFFFF0000/);
  assert.match(exported, /  mist_jade:[\s\S]*?benter: 0xFF0F766E/, "完整导出应保留内置主题及功能键色");
  assert.match(exported, /key_back_color: bbs/, "完整导出应保留功能键颜色绑定");
  assert.match(exported, /preset_keyboards:/, "完整导出应保留键盘布局");

  await page.locator("#undoButton").click();
  assert.equal(await page.locator("#dirtyBadge").isVisible(), false, "撤销后应恢复干净状态");

  const rootImageInput = page.locator('.color-value-input[data-color-key="root_background"]');
  await rootImageInput.fill("paper.png");
  await rootImageInput.dispatchEvent("change");
  assert.equal(await page.locator('[data-color-row="root_background"] .image-badge').count(), 1, "背景图片路径应被保留");
  await page.locator("#undoButton").click();

  const keyboardImageInput = page.locator('.color-value-input[data-color-key="keyboard_back_color"]');
  await keyboardImageInput.fill("keyboard-paper.png");
  await keyboardImageInput.dispatchEvent("change");
  assert.equal(await page.locator('[data-color-row="keyboard_back_color"] .image-badge').count(), 1, "官方键盘背景应保留图片路径");
  await page.locator("#undoButton").click();

  for (const themeId of themeIds) {
    await page.locator(`[data-theme-id="${themeId}"]`).click();
    assert.equal(await page.locator("#previewThemeId").textContent(), themeId);
    const keyStyles = await page.evaluate(() => {
      const styleFor = (title) => {
        const style = getComputedStyle(document.querySelector(`.trime-key[title="${title}"]`));
        return { background: style.backgroundColor, foreground: style.color };
      };
      return {
        regular: styleFor("q"),
        function: styleFor("Shift_L"),
        backspace: styleFor("BackSpace"),
        space: styleFor("space"),
        enter: styleFor("Return"),
      };
    });
    const specialKeys = Object.entries(keyStyles).filter(([name]) => name !== "regular");
    specialKeys.forEach(([name, colors]) => {
      assert.notEqual(colors.background, keyStyles.regular.background, `${themeId} 的 ${name} 应区别于普通键`);
      assert.ok(cssContrastRatio(colors.foreground, colors.background) >= 4.5, `${themeId} 的 ${name} 文字对比度应达到 4.5:1`);
    });
    assert.equal(new Set(specialKeys.map(([, colors]) => colors.background)).size, 4, `${themeId} 的四类功能键应使用不同背景`);
  }
  for (const layoutId of layoutIds) {
    await page.locator("#layoutSelect").selectOption(layoutId);
    assert.ok(await page.locator(".trime-key").count() > 0, `${layoutId} 应能渲染键位`);
  }
  await page.locator('[data-theme-id="default"]').click();
  await page.locator("#layoutSelect").selectOption("default");
  await page.locator('[data-preview-state="pressed"]').click();
  assert.equal(await page.locator(".key-popup-demo").count(), 1, "按下状态应显示新版 popup 配色预览");
  await page.locator('[data-preview-state="normal"]').click();

  await page.locator('[data-inspector-mode="layout"]').click();
  assert.equal(await page.locator("#layoutInspectorBody").isVisible(), true, "应能切换到布局编辑器");
  assert.equal(await page.locator("#layoutKeyCount").textContent(), `${initialKeyCount} 键`);
  assert.equal(await page.locator('.trime-key[draggable="true"]').count(), initialKeyCount, "布局模式中的按键应可拖动");

  await page.locator('[data-key-index="0"]').click();
  await page.locator("#addKeyButton").click();
  assert.equal(await page.locator(".trime-key").count(), initialKeyCount + 1, "添加按键后预览应立即增加");

  const clickInput = page.locator('[data-key-prop="click"]');
  await clickInput.fill("TestKey");
  await clickInput.press("Tab");
  const widthInput = page.locator('[data-key-prop="width"]');
  await widthInput.fill("12.5");
  await widthInput.press("Tab");
  assert.equal(await page.locator(".trime-key.layout-selected span").textContent(), "TestKey", "按键属性应实时反映到预览");

  await page.locator('[data-key-move="1"]').click();
  assert.match(await page.locator("#selectedKeyPosition").textContent(), /^#3\b/, "前后移动按钮应改变按键顺序");
  await page.locator('[data-key-index="2"]').dragTo(page.locator('[data-key-index="5"]'));
  assert.notEqual(await page.locator("#selectedKeyPosition").textContent(), "#3 · 第 1 行", "拖拽应改变按键顺序");

  await page.locator("#exportButton").click();
  const layoutDownloadPromise = page.waitForEvent("download");
  await page.locator("#downloadFullButton").click();
  const layoutDownload = await layoutDownloadPromise;
  const layoutStream = await layoutDownload.createReadStream();
  let layoutExported = "";
  for await (const chunk of layoutStream) layoutExported += chunk.toString("utf8");
  assert.match(layoutExported, /- \{click: TestKey, width: 12\.5\}/, "完整导出应写入新增按键");
  assert.match(layoutExported, /preset_color_schemes:/, "布局导出应保留配色配置");
  const validationPage = await browser.newPage({ viewport: { width: 1100, height: 760 } });
  validationPage.on("pageerror", (error) => errors.push(error.message));
  await validationPage.goto(pathToFileURL(path.resolve(__dirname, "index.html")).href, {
    waitUntil: "domcontentloaded",
  });
  await validationPage.locator("#fileInput").setInputFiles({
    name: "layout-export.trime.yaml",
    mimeType: "text/yaml",
    buffer: Buffer.from(layoutExported, "utf8"),
  });
  await validationPage.waitForFunction(() => document.querySelector("#sourceName")?.textContent === "layout-export.trime.yaml");
  assert.equal(await validationPage.locator(".trime-key").count(), initialKeyCount + 1, "导出的布局应能重新载入");
  assert.equal(await validationPage.locator(".theme-card").count(), 6, "导出的配置应保留默认主题与 5 套新主题");
  await validationPage.close();

  await page.locator("#deleteKeyButton").click();
  assert.equal(await page.locator(".trime-key").count(), initialKeyCount, "删除新增按键后应恢复原数量");
  assert.equal(await page.locator("#dirtyBadge").isVisible(), false, "恢复原布局后应清除布局修改状态");
  await page.locator("#undoButton").click();
  assert.equal(await page.locator(".trime-key").count(), initialKeyCount + 1, "撤销删除应恢复新增按键");
  await page.locator("#resetLayoutButton").click();
  assert.equal(await page.locator(".trime-key").count(), initialKeyCount, "重置布局应恢复模板按键");
  assert.equal(await page.locator("#dirtyBadge").isVisible(), false, "重置布局后应恢复干净状态");

  await page.waitForTimeout(2000);
  await page.screenshot({
    path: process.env.TRIME_TEST_SCREENSHOT || path.resolve(__dirname, "trime-theme-studio.png"),
    fullPage: true,
  });

  assert.equal(await page.locator('[data-color-row="popup_back_color"]').count(), 1, "应显示新版 popup 字段");
  assert.deepEqual(errors, [], `浏览器错误：${errors.join("; ")}`);
  console.log(JSON.stringify({
    themes: themeIds.length,
    layouts: initialLayoutCount,
    keys: initialKeyCount,
    colorRows: initialColorRowCount,
    layoutEditing: true,
    screenshot: "trime-theme-studio.png",
  }, null, 2));
  await browser.close();
})().catch((error) => {
  console.error(error.stack || error);
  process.exit(1);
});
