const assert = require("node:assert/strict");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const { chromium } = require("playwright");

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
  assert.equal(await page.locator(".theme-card").count(), 1, "初始模板应显示 1 套主题");
  const initialLayoutCount = await page.locator("#layoutSelect option").count();
  const initialKeyCount = await page.locator(".trime-key").count();
  const initialColorRowCount = await page.locator(".color-row").count();
  assert.equal(initialLayoutCount, 3, "应读取模板中的 3 个实体键盘布局");
  assert.ok(initialKeyCount >= 30, "应渲染完整键盘");
  assert.ok(initialColorRowCount >= 42, "应渲染官方完整颜色字段");
  assert.equal(await page.locator(".deprecated-badge").count(), 2, "两个 preview_* 旧字段应标记为已废弃");
  const themeIds = await page.locator(".theme-card").evaluateAll((cards) => cards.map((card) => card.dataset.themeId));
  const layoutIds = await page.locator("#layoutSelect option").evaluateAll((options) => options.map((option) => option.value));

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
  assert.match(exported, /preset_keyboards:/, "完整导出应保留键盘布局");

  await page.locator("#undoButton").click();
  assert.equal(await page.locator("#dirtyBadge").isVisible(), false, "撤销后应恢复干净状态");

  const keyboardImageInput = page.locator('.color-value-input[data-color-key="keyboard_background"]');
  await keyboardImageInput.fill("paper.png");
  await keyboardImageInput.dispatchEvent("change");
  assert.equal(await page.locator('[data-color-row="keyboard_background"] .image-badge').count(), 1, "背景图片路径应被保留");
  await page.locator("#undoButton").click();

  for (const themeId of themeIds) {
    await page.locator(`[data-theme-id="${themeId}"]`).click();
    assert.equal(await page.locator("#previewThemeId").textContent(), themeId);
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
  await page.screenshot({ path: path.resolve(__dirname, "trime-theme-studio.png"), fullPage: true });

  assert.equal(await page.locator('[data-color-row="popup_back_color"]').count(), 1, "应显示新版 popup 字段");
  assert.deepEqual(errors, [], `浏览器错误：${errors.join("; ")}`);
  console.log(JSON.stringify({
    themes: 1,
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
