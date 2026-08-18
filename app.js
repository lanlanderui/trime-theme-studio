(() => {
  "use strict";

  const FALLBACK_COLORS = {
    candidate_text_color: "text_color",
    comment_text_color: "candidate_text_color",
    border_color: "back_color",
    candidate_separator_color: "border_color",
    hilited_text_color: "text_color",
    hilited_back_color: "back_color",
    hilited_candidate_text_color: "hilited_text_color",
    hilited_candidate_back_color: "hilited_back_color",
    hilited_candidate_button_color: "hilited_candidate_back_color",
    hilited_label_color: "hilited_candidate_text_color",
    hilited_comment_text_color: "comment_text_color",
    hilited_key_back_color: "hilited_candidate_back_color",
    hilited_key_text_color: "hilited_candidate_text_color",
    hilited_key_symbol_color: "hilited_comment_text_color",
    hilited_off_key_back_color: "hilited_key_back_color",
    hilited_on_key_back_color: "hilited_key_back_color",
    hilited_off_key_text_color: "hilited_key_text_color",
    hilited_on_key_text_color: "hilited_key_text_color",
    key_back_color: "back_color",
    key_border_color: "border_color",
    key_text_color: "candidate_text_color",
    key_symbol_color: "comment_text_color",
    label_color: "candidate_text_color",
    off_key_back_color: "key_back_color",
    off_key_text_color: "key_text_color",
    on_key_back_color: "hilited_key_back_color",
    on_key_text_color: "hilited_key_text_color",
    popup_back_color: "key_back_color",
    popup_text_color: "key_text_color",
    hilited_popup_back_color: "hilited_key_back_color",
    hilited_popup_text_color: "hilited_key_text_color",
    shadow_color: "border_color",
    root_background: "back_color",
    candidate_background: "back_color",
    keyboard_back_color: "border_color",
    keyboard_background: "keyboard_back_color",
    liquid_keyboard_background: "keyboard_back_color",
    text_back_color: "back_color",
    long_text_color: "key_text_color",
    long_text_back_color: "key_back_color",
  };

  const BASE_COLORS = {
    back_color: "0xFFFFFFFF",
    text_color: "0xFF20292D",
    keyboard_back_color: "0xFFE6EAEB",
    key_back_color: "0xFFFFFFFF",
    key_text_color: "0xFF20292D",
    hilited_key_back_color: "0xFF087F73",
    hilited_key_text_color: "0xFFFFFFFF",
    root_background: "0xFFFFFFFF",
    candidate_background: "0xFFFFFFFF",
    keyboard_background: "0xFFE6EAEB",
    popup_back_color: "0xFFFFFFFF",
    popup_text_color: "0xFF20292D",
  };

  const COLOR_GROUPS = [
    {
      title: "基础与候选栏",
      keys: [
        "root_background",
        "back_color",
        "candidate_background",
        "text_back_color",
        "text_color",
        "candidate_text_color",
        "comment_text_color",
        "candidate_separator_color",
        "border_color",
        "label_color",
      ],
    },
    {
      title: "键盘与按键",
      keys: [
        "keyboard_back_color",
        "keyboard_background",
        "liquid_keyboard_background",
        "key_back_color",
        "key_border_color",
        "key_text_color",
        "key_symbol_color",
        "shadow_color",
        "long_text_back_color",
        "long_text_color",
      ],
    },
    {
      title: "按键弹出层",
      keys: [
        "popup_back_color",
        "popup_text_color",
        "hilited_popup_back_color",
        "hilited_popup_text_color",
      ],
    },
    {
      title: "高亮与开关状态",
      keys: [
        "hilited_back_color",
        "hilited_text_color",
        "hilited_candidate_back_color",
        "hilited_candidate_text_color",
        "hilited_candidate_button_color",
        "hilited_label_color",
        "hilited_comment_text_color",
        "hilited_key_back_color",
        "hilited_key_text_color",
        "hilited_key_symbol_color",
        "off_key_back_color",
        "off_key_text_color",
        "on_key_back_color",
        "on_key_text_color",
        "hilited_off_key_back_color",
        "hilited_off_key_text_color",
        "hilited_on_key_back_color",
        "hilited_on_key_text_color",
      ],
    },
  ];

  const COLOR_LABELS = {
    root_background: "输入法整体背景",
    back_color: "候选区背景",
    candidate_background: "候选栏整体背景",
    text_back_color: "编码区背景",
    text_color: "编码文字",
    candidate_text_color: "候选文字",
    comment_text_color: "候选提示",
    candidate_separator_color: "候选分割线",
    border_color: "整体边框",
    label_color: "候选序号",
    hilited_label_color: "高亮候选序号",
    hilited_candidate_button_color: "候选工具按钮按下背景",
    keyboard_back_color: "键盘基础背景色",
    keyboard_background: "键盘背景",
    liquid_keyboard_background: "液态键盘背景",
    key_back_color: "普通按键背景",
    key_border_color: "按键边框",
    key_text_color: "普通按键文字",
    key_symbol_color: "按键角标",
    shadow_color: "按键阴影",
    popup_back_color: "弹出层背景",
    popup_text_color: "弹出层文字",
    hilited_popup_back_color: "弹出层选中背景",
    hilited_popup_text_color: "弹出层选中文字",
    long_text_back_color: "长文本按键背景",
    long_text_color: "长文本按键文字",
    preview_back_color: "旧版按键提示背景",
    preview_text_color: "旧版按键提示文字",
    hilited_back_color: "高亮编码背景",
    hilited_text_color: "高亮编码文字",
    hilited_candidate_back_color: "高亮候选背景",
    hilited_candidate_text_color: "高亮候选文字",
    hilited_comment_text_color: "高亮提示文字",
    hilited_key_back_color: "按下按键背景",
    hilited_key_text_color: "按下按键文字",
    hilited_key_symbol_color: "按下按键角标",
    off_key_back_color: "关闭状态背景",
    off_key_text_color: "关闭状态文字",
    on_key_back_color: "开启状态背景",
    on_key_text_color: "开启状态文字",
    hilited_off_key_back_color: "按下关闭状态背景",
    hilited_off_key_text_color: "按下关闭状态文字",
    hilited_on_key_back_color: "按下开启状态背景",
    hilited_on_key_text_color: "按下开启状态文字",
    bkg: "空格键背景",
    tkg: "空格键文字",
    benter: "回车键背景",
    tenter: "回车键文字",
    bgn: "功能键背景",
    tgn: "功能键文字",
    bbs: "退格键背景",
    tbs: "退格键文字",
    baoe: "韵母键背景",
    taoe: "韵母键文字",
    bh1: "第一行按键背景",
    th1: "第一行按键文字",
    bh2: "第二行按键背景",
    th2: "第二行按键文字",
    bh3: "第三行按键背景",
    th3: "第三行按键文字",
    bh4: "第四行按键背景",
    th4: "第四行按键文字",
  };

  const CUSTOM_COLOR_KEYS = new Set([
    "bkg", "tkg", "benter", "tenter", "bgn", "tgn", "bbs", "tbs",
    "baoe", "taoe", "bh1", "th1", "bh2", "th2", "bh3", "th3", "bh4", "th4",
  ]);

  const OFFICIAL_COLOR_KEYS = new Set(COLOR_GROUPS.flatMap((group) => group.keys));

  const LEGACY_COLOR_KEYS = new Map([
    ["preview_back_color", "popup_back_color"],
    ["preview_text_color", "popup_text_color"],
  ]);

  const DRAWABLE_COLOR_KEYS = new Set([
    "root_background",
    "candidate_background",
    "keyboard_background",
    "liquid_keyboard_background",
    "back_color",
    "key_back_color",
    "hilited_key_back_color",
    "off_key_back_color",
    "on_key_back_color",
    "popup_back_color",
    "hilited_popup_back_color",
    "long_text_back_color",
  ]);

  const CANDIDATES = [
    ["同文", "1"],
    ["通闻", "2"],
    ["同问", "3"],
    ["桐纹", "4"],
  ];

  const dom = {};
  const state = {
    sourceText: "",
    sourceName: "trime.yaml",
    parsed: null,
    changes: {},
    layoutChanges: {},
    activeThemeId: "",
    activeLayoutId: "default",
    inspectorMode: "color",
    selectedKeyIndex: 0,
    draggedKeyIndex: -1,
    themeFilter: "all",
    themeSearch: "",
    colorSearch: "",
    previewState: "normal",
    pressedKeyIndex: -1,
    undoStack: [],
    redoStack: [],
    toastTimer: 0,
    androidFileUri: "",
    androidFiles: [],
  };

  function cacheDom() {
    [
      "sourceName", "sourceMeta", "undoButton", "importButton", "exportButton",
      "androidRimeBar", "androidOpenFileButton", "androidOpenFolderButton", "androidFileSelect",
      "androidSaveButton", "androidSaveAsButton", "androidFileStatus",
      "themeCount", "themeSearch", "themeFilters", "themeList", "themeEmpty",
      "clearSearchButton", "previewThemeName", "previewThemeId", "layoutSelect",
      "phoneShell", "imePreview", "candidateRow", "keyboardPreview", "contrastInsights",
      "inspectorTitle", "dirtyBadge", "colorInspectorHead", "layoutInspectorHead",
      "colorInspectorBody", "layoutInspectorBody", "themeNameInput", "themeAuthorInput", "colorSearch",
      "resetThemeButton", "colorEditor", "colorEmpty", "fileInput", "dropOverlay",
      "layoutEditorName", "layoutKeyCount", "addKeyButton", "resetLayoutButton",
      "selectedKeyTitle", "selectedKeyPosition", "deleteKeyButton", "keyOrderList",
      "exportDialog", "downloadFullButton", "copySchemeButton", "downloadSchemeButton",
      "exportSummary", "resetAllButton", "toast",
    ].forEach((id) => {
      dom[id] = document.getElementById(id);
    });
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function countIndent(line) {
    const match = line.match(/^ */);
    return match ? match[0].length : 0;
  }

  function splitYamlValue(rest) {
    let quote = "";
    let escaped = false;
    for (let i = 0; i < rest.length; i += 1) {
      const char = rest[i];
      if (escaped) {
        escaped = false;
        continue;
      }
      if (quote === '"' && char === "\\") {
        escaped = true;
        continue;
      }
      if (quote) {
        if (char === quote) quote = "";
        continue;
      }
      if (char === '"' || char === "'") {
        quote = char;
        continue;
      }
      if (char === "#" && (i === 0 || /\s/.test(rest[i - 1]))) {
        const before = rest.slice(0, i).trimEnd();
        const gapStart = before.length;
        return {
          rawValue: before,
          comment: rest.slice(gapStart),
        };
      }
    }
    return { rawValue: rest.trimEnd(), comment: "" };
  }

  function parseScalar(raw) {
    const value = String(raw ?? "").trim();
    if (value.length >= 2 && value[0] === '"' && value.at(-1) === '"') {
      try {
        return JSON.parse(value);
      } catch {
        return value.slice(1, -1)
          .replace(/\\"/g, '"')
          .replace(/\\\\/g, "\\");
      }
    }
    if (value.length >= 2 && value[0] === "'" && value.at(-1) === "'") {
      return value.slice(1, -1).replace(/''/g, "'");
    }
    return value;
  }

  function quoteStyle(raw) {
    const value = String(raw ?? "").trim();
    if (value.startsWith('"') && value.endsWith('"')) return "double";
    if (value.startsWith("'") && value.endsWith("'")) return "single";
    return "plain";
  }

  function findSectionEnd(lines, startIndex) {
    for (let index = startIndex + 1; index < lines.length; index += 1) {
      const line = lines[index];
      if (!line.trim() || line.trimStart().startsWith("#")) continue;
      if (countIndent(line) === 0) return index;
    }
    return lines.length;
  }

  function parseSimpleSection(lines, sectionName) {
    const start = lines.findIndex((line) => line.trim() === `${sectionName}:` && countIndent(line) === 0);
    const result = {};
    if (start < 0) return result;
    const end = findSectionEnd(lines, start);
    for (let index = start + 1; index < end; index += 1) {
      const match = lines[index].match(/^ {2}([A-Za-z0-9_.-]+):\s*(.*)$/);
      if (!match) continue;
      const { rawValue } = splitYamlValue(match[2]);
      result[match[1]] = parseScalar(rawValue);
    }
    return result;
  }

  function splitTopLevel(value, delimiter = ",") {
    const parts = [];
    let buffer = "";
    let quote = "";
    let escaped = false;
    let square = 0;
    let curly = 0;
    let round = 0;

    for (const char of value) {
      if (escaped) {
        buffer += char;
        escaped = false;
        continue;
      }
      if (quote === '"' && char === "\\") {
        buffer += char;
        escaped = true;
        continue;
      }
      if (quote) {
        buffer += char;
        if (char === quote) quote = "";
        continue;
      }
      if (char === '"' || char === "'") {
        quote = char;
        buffer += char;
        continue;
      }
      if (char === "[") square += 1;
      if (char === "]") square -= 1;
      if (char === "{") curly += 1;
      if (char === "}") curly -= 1;
      if (char === "(") round += 1;
      if (char === ")") round -= 1;

      if (char === delimiter && square === 0 && curly === 0 && round === 0) {
        parts.push(buffer.trim());
        buffer = "";
      } else {
        buffer += char;
      }
    }
    if (buffer.trim()) parts.push(buffer.trim());
    return parts;
  }

  function splitKeyValue(value) {
    let quote = "";
    let escaped = false;
    let square = 0;
    for (let index = 0; index < value.length; index += 1) {
      const char = value[index];
      if (escaped) {
        escaped = false;
        continue;
      }
      if (quote === '"' && char === "\\") {
        escaped = true;
        continue;
      }
      if (quote) {
        if (char === quote) quote = "";
        continue;
      }
      if (char === '"' || char === "'") {
        quote = char;
        continue;
      }
      if (char === "[") square += 1;
      if (char === "]") square -= 1;
      if (char === ":" && square === 0) {
        return [value.slice(0, index).trim(), value.slice(index + 1).trim()];
      }
    }
    return [value.trim(), ""];
  }

  function parseInlineMap(value) {
    const result = {};
    splitTopLevel(value).forEach((part) => {
      const [key, rawValue] = splitKeyValue(part);
      if (key) result[key] = parseScalar(rawValue);
    });
    return result;
  }

  function parseThemes(lines) {
    const start = lines.findIndex((line) => line.trim() === "preset_color_schemes:" && countIndent(line) === 0);
    if (start < 0) return { start: -1, end: -1, themes: [], themeMap: {} };
    const end = findSectionEnd(lines, start);
    const themes = [];
    const themeMap = {};
    let current = null;

    for (let index = start + 1; index < end; index += 1) {
      const line = lines[index];
      const header = line.match(/^ {2}([A-Za-z0-9_.-]+):\s*(?:#.*)?$/);
      if (header) {
        if (current) current.end = index;
        current = {
          id: header[1],
          start: index,
          end,
          props: {},
          propertyOrder: [],
          propertyMeta: {},
          lastPropertyLine: index,
        };
        themes.push(current);
        themeMap[current.id] = current;
        continue;
      }
      if (!current) continue;
      const property = line.match(/^ {4}([A-Za-z0-9_.-]+):(\s*)(.*)$/);
      if (!property) continue;
      const parts = splitYamlValue(property[3]);
      const key = property[1];
      const rawValue = parts.rawValue.trim();
      current.props[key] = parseScalar(rawValue);
      current.propertyOrder.push(key);
      current.propertyMeta[key] = {
        lineIndex: index,
        prefix: `    ${key}:${property[2]}`,
        rawValue,
        comment: parts.comment,
        quote: quoteStyle(rawValue),
      };
      current.lastPropertyLine = index;
    }
    if (current) current.end = end;
    return { start, end, themes, themeMap };
  }

  function parseKeyboards(lines, style) {
    const start = lines.findIndex((line) => line.trim() === "preset_keyboards:" && countIndent(line) === 0);
    if (start < 0) return { start: -1, end: -1, layouts: [], layoutMap: {} };
    const end = findSectionEnd(lines, start);
    const layouts = [];
    const layoutMap = {};
    let current = null;
    let inKeys = false;

    for (let index = start + 1; index < end; index += 1) {
      const line = lines[index];
      const header = line.match(/^ {2}([A-Za-z0-9_.-]+):\s*(?:#.*)?$/);
      if (header) {
        if (current) current.end = index;
        current = {
          id: header[1],
          start: index,
          end,
          name: header[1],
          width: Number(style.key_width) || 10,
          keys: [],
          keysHeaderLine: -1,
          keyLineIndices: [],
          keyIndent: "    ",
        };
        layouts.push(current);
        layoutMap[current.id] = current;
        inKeys = false;
        continue;
      }
      if (!current) continue;
      const property = line.match(/^ {4}([A-Za-z0-9_.-]+):\s*(.*)$/);
      if (property && property[1] !== "keys") {
        if (inKeys) inKeys = false;
        const { rawValue } = splitYamlValue(property[2]);
        const parsedValue = parseScalar(rawValue);
        if (property[1] === "name") current.name = parsedValue;
        if (property[1] === "width") current.width = Number(parsedValue) || current.width;
      }
      if (/^ {4}keys:\s*$/.test(line)) {
        inKeys = true;
        current.keysHeaderLine = index;
        continue;
      }
      if (!inKeys) continue;
      const keyLine = line.match(/^( {4,})-\s*\{(.*)\}\s*(?:#.*)?$/);
      if (keyLine) {
        current.keyIndent = keyLine[1];
        current.keys.push(parseInlineMap(keyLine[2]));
        current.keyLineIndices.push(index);
      }
    }
    if (current) current.end = end;
    return { start, end, layouts, layoutMap };
  }

  function parseTrimeYaml(text) {
    const originalText = String(text ?? "").replace(/^\uFEFF/, "");
    const eol = originalText.includes("\r\n") ? "\r\n" : "\n";
    const hasFinalEol = /\r?\n$/.test(originalText);
    const lines = originalText.replace(/\r\n/g, "\n").split("\n");
    const anchors = {};

    lines.forEach((line) => {
      const match = line.match(/&([A-Za-z0-9_.-]+)\s+(0x[0-9A-Fa-f]{2,8})\b/);
      if (match) anchors[match[1]] = match[2];
    });

    const style = parseSimpleSection(lines, "style");
    const configuredFallbacks = parseSimpleSection(lines, "fallback_colors");
    const themeResult = parseThemes(lines);
    const keyboardResult = parseKeyboards(lines, style);

    return {
      originalText,
      eol,
      hasFinalEol,
      lines,
      anchors,
      style,
      fallbacks: { ...FALLBACK_COLORS, ...configuredFallbacks },
      ...themeResult,
      ...keyboardResult,
    };
  }

  function themeChanges(themeId) {
    if (!state.changes[themeId]) state.changes[themeId] = {};
    return state.changes[themeId];
  }

  function hasOwn(object, key) {
    return Object.prototype.hasOwnProperty.call(object, key);
  }

  function getEffectiveProps(themeId) {
    const theme = state.parsed.themeMap[themeId];
    return { ...theme.props, ...(state.changes[themeId] || {}) };
  }

  function cloneKeys(keys) {
    return keys.map((key) => ({ ...key }));
  }

  function keysEqual(left, right) {
    return JSON.stringify(left) === JSON.stringify(right);
  }

  function getEffectiveLayout(layoutId = state.activeLayoutId) {
    const layout = state.parsed.layoutMap[layoutId];
    if (!layout) return null;
    return {
      ...layout,
      keys: state.layoutChanges[layoutId]
        ? cloneKeys(state.layoutChanges[layoutId])
        : cloneKeys(layout.keys),
    };
  }

  function isLayoutChanged(layoutId) {
    return hasOwn(state.layoutChanges, layoutId);
  }

  function setLayoutKeys(layoutId, keys) {
    const layout = state.parsed.layoutMap[layoutId];
    if (!layout) return;
    if (keysEqual(keys, layout.keys)) delete state.layoutChanges[layoutId];
    else state.layoutChanges[layoutId] = cloneKeys(keys);
  }

  function isThemeChanged(themeId) {
    return Object.keys(state.changes[themeId] || {}).length > 0;
  }

  function changedThemeCount() {
    return Object.keys(state.changes).filter(isThemeChanged).length;
  }

  function changedValueCount() {
    return Object.values(state.changes).reduce((sum, changes) => sum + Object.keys(changes).length, 0);
  }

  function changedLayoutCount() {
    return Object.keys(state.layoutChanges).length;
  }

  function currentChangeToken(themeId, key) {
    const changes = state.changes[themeId] || {};
    return hasOwn(changes, key)
      ? { present: true, value: changes[key] }
      : { present: false, value: "" };
  }

  function tokensEqual(left, right) {
    return left.present === right.present && (!left.present || left.value === right.value);
  }

  function setChangeToken(themeId, key, token) {
    const changes = themeChanges(themeId);
    if (token.present) changes[key] = token.value;
    else delete changes[key];
    if (Object.keys(changes).length === 0) delete state.changes[themeId];
  }

  function setLiveValue(themeId, key, value) {
    const theme = state.parsed.themeMap[themeId];
    if (!theme) return;
    const original = theme.props[key];
    if (hasOwn(theme.props, key) && value === original) {
      setChangeToken(themeId, key, { present: false, value: "" });
    } else {
      setChangeToken(themeId, key, { present: true, value });
    }
  }

  function pushHistory(items) {
    const meaningful = items.filter((item) => !tokensEqual(item.before, item.after));
    if (!meaningful.length) return;
    state.undoStack.push(meaningful);
    if (state.undoStack.length > 100) state.undoStack.shift();
    state.redoStack = [];
  }

  function pushLayoutHistory(layoutId, before, after) {
    if (keysEqual(before, after)) return;
    state.undoStack.push({
      type: "layout",
      layoutId,
      before: cloneKeys(before),
      after: cloneKeys(after),
    });
    if (state.undoStack.length > 100) state.undoStack.shift();
    state.redoStack = [];
  }

  function historyFromElement(element, themeId, key) {
    let before = { present: false, value: "" };
    try {
      before = JSON.parse(element.dataset.beforeToken || "{}");
    } catch {
      before = { present: false, value: "" };
    }
    const after = currentChangeToken(themeId, key);
    pushHistory([{ themeId, key, before, after }]);
  }

  function undo() {
    const action = state.undoStack.pop();
    if (!action) return;
    if (Array.isArray(action)) {
      [...action].reverse().forEach((item) => setChangeToken(item.themeId, item.key, item.before));
    } else if (action.type === "layout") {
      setLayoutKeys(action.layoutId, action.before);
      state.selectedKeyIndex = Math.min(state.selectedKeyIndex, Math.max(0, action.before.length - 1));
    } else if (action.type === "reset-all") {
      action.themeItems.forEach((item) => setChangeToken(item.themeId, item.key, item.before));
      action.layoutItems.forEach((item) => setLayoutKeys(item.layoutId, item.before));
    }
    state.redoStack.push(action);
    renderAll();
    toast("已撤销上一步修改");
  }

  function redo() {
    const action = state.redoStack.pop();
    if (!action) return;
    if (Array.isArray(action)) {
      action.forEach((item) => setChangeToken(item.themeId, item.key, item.after));
    } else if (action.type === "layout") {
      setLayoutKeys(action.layoutId, action.after);
      state.selectedKeyIndex = Math.min(state.selectedKeyIndex, Math.max(0, action.after.length - 1));
    } else if (action.type === "reset-all") {
      action.themeItems.forEach((item) => setChangeToken(item.themeId, item.key, item.after));
      action.layoutItems.forEach((item) => setLayoutKeys(item.layoutId, item.after));
    }
    state.undoStack.push(action);
    renderAll();
    toast("已重做修改");
  }

  function isImageValue(value) {
    return /\.(?:png|webp|jpg|gif)$/i.test(String(value ?? "").trim());
  }

  function normalizeColorValue(value, key) {
    let raw = String(value ?? "").trim();
    if (raw.startsWith("*")) {
      return hasOwn(state.parsed.anchors, raw.slice(1)) ? raw : null;
    }
    if (DRAWABLE_COLOR_KEYS.has(key) && isImageValue(raw)) return raw;
    raw = raw.replace(/^#/, "").replace(/^0x/i, "");
    if (!/^[0-9A-Fa-f]+$/.test(raw)) return null;
    if (raw.length === 2 || raw.length === 6 || raw.length === 8) {
      if (raw.length === 6) raw = `FF${raw}`;
      return `0x${raw.toUpperCase()}`;
    }
    return null;
  }

  function parseColor(value, seenAnchors = new Set()) {
    let raw = String(value ?? "").trim();
    if (raw.startsWith("*")) {
      const anchor = raw.slice(1);
      if (seenAnchors.has(anchor)) return null;
      seenAnchors.add(anchor);
      return parseColor(state.parsed?.anchors?.[anchor], seenAnchors);
    }
    raw = raw.replace(/^#/, "").replace(/^0x/i, "");
    if (!/^[0-9A-Fa-f]+$/.test(raw)) return null;
    if (raw.length === 2) {
      return { a: parseInt(raw, 16), r: 0, g: 0, b: 0 };
    }
    if (raw.length === 6) raw = `FF${raw}`;
    if (raw.length !== 8) return null;
    return {
      a: parseInt(raw.slice(0, 2), 16),
      r: parseInt(raw.slice(2, 4), 16),
      g: parseInt(raw.slice(4, 6), 16),
      b: parseInt(raw.slice(6, 8), 16),
    };
  }

  function resolveColor(themeId, key, seen = new Set()) {
    const cycleKey = `${themeId}:${key}`;
    if (seen.has(cycleKey)) return parseColor(BASE_COLORS[key] || BASE_COLORS.back_color);
    seen.add(cycleKey);

    const props = getEffectiveProps(themeId);
    const value = props[key];
    if (value !== undefined) {
      const direct = parseColor(value);
      if (direct) return direct;
    }

    const fallback = state.parsed.fallbacks[key];
    if (fallback) return resolveColor(themeId, fallback, seen);
    return parseColor(BASE_COLORS[key] || BASE_COLORS.back_color);
  }

  function resolveToken(themeId, token, fallbackKey) {
    if (token) {
      const direct = parseColor(token);
      if (direct) return direct;
      const props = getEffectiveProps(themeId);
      if (hasOwn(props, token)) return resolveColor(themeId, token);
    }
    return resolveColor(themeId, fallbackKey);
  }

  function colorToCss(color) {
    if (!color) return "rgba(0, 0, 0, 0)";
    return `rgba(${color.r}, ${color.g}, ${color.b}, ${(color.a / 255).toFixed(3)})`;
  }

  function colorToRgbHex(color) {
    if (!color) return "#000000";
    return `#${[color.r, color.g, color.b].map((channel) => channel.toString(16).padStart(2, "0")).join("")}`;
  }

  function colorToArgb(color) {
    if (!color) return "0x00000000";
    return `0x${[color.a, color.r, color.g, color.b]
      .map((channel) => channel.toString(16).padStart(2, "0"))
      .join("")
      .toUpperCase()}`;
  }

  function withRgb(color, rgbHex) {
    const rgb = parseColor(rgbHex);
    return {
      a: color?.a ?? 255,
      r: rgb?.r ?? 0,
      g: rgb?.g ?? 0,
      b: rgb?.b ?? 0,
    };
  }

  function composite(color, background = { a: 255, r: 255, g: 255, b: 255 }) {
    const alpha = (color?.a ?? 255) / 255;
    return {
      a: 255,
      r: Math.round((color?.r ?? 0) * alpha + background.r * (1 - alpha)),
      g: Math.round((color?.g ?? 0) * alpha + background.g * (1 - alpha)),
      b: Math.round((color?.b ?? 0) * alpha + background.b * (1 - alpha)),
    };
  }

  function relativeLuminance(color) {
    const opaque = composite(color);
    const channels = [opaque.r, opaque.g, opaque.b].map((channel) => {
      const value = channel / 255;
      return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
  }

  function contrastRatio(foreground, background) {
    const backgroundOpaque = composite(background);
    const foregroundOpaque = composite(foreground, backgroundOpaque);
    const first = relativeLuminance(foregroundOpaque);
    const second = relativeLuminance(backgroundOpaque);
    return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
  }

  function isDarkTheme(themeId) {
    const background = resolveColor(themeId, "keyboard_back_color");
    return relativeLuminance(background) < 0.38;
  }

  function themeDisplayName(themeId) {
    const props = getEffectiveProps(themeId);
    return props.name || themeId;
  }

  function renderThemeList() {
    const search = state.themeSearch.trim().toLocaleLowerCase();
    const filtered = state.parsed.themes.filter((theme) => {
      const props = getEffectiveProps(theme.id);
      const haystack = `${theme.id} ${props.name || ""} ${props.author || ""}`.toLocaleLowerCase();
      if (search && !haystack.includes(search)) return false;
      if (state.themeFilter === "dark" && !isDarkTheme(theme.id)) return false;
      if (state.themeFilter === "light" && isDarkTheme(theme.id)) return false;
      if (state.themeFilter === "changed" && !isThemeChanged(theme.id)) return false;
      return true;
    });

    dom.themeCount.textContent = state.parsed.themes.length;
    dom.themeEmpty.hidden = filtered.length > 0;
    dom.themeList.innerHTML = filtered.map((theme) => {
      const id = theme.id;
      const props = getEffectiveProps(id);
      const keyboard = colorToCss(resolveColor(id, "keyboard_back_color"));
      const key = colorToCss(resolveColor(id, "key_back_color"));
      const accent = colorToCss(resolveColor(id, "hilited_candidate_back_color"));
      const text = colorToCss(resolveColor(id, "key_text_color"));
      return `
        <button class="theme-card${id === state.activeThemeId ? " active" : ""}" type="button" data-theme-id="${escapeHtml(id)}">
          <span class="theme-swatch" style="--swatch-keyboard:${keyboard};--swatch-key:${key};--swatch-accent:${accent};--swatch-text:${text}">
            <i></i><i></i><i></i>
          </span>
          <span class="theme-card-copy">
            <strong>${escapeHtml(props.name || id)}</strong>
            <small>${escapeHtml(id)}${props.author ? ` · ${escapeHtml(props.author)}` : ""}</small>
          </span>
          <span class="theme-card-state">
            ${isThemeChanged(id) ? '<i class="changed-dot" title="已修改"></i>' : ""}
            <span>›</span>
          </span>
        </button>
      `;
    }).join("");
  }

  function renderLayoutSelect() {
    const layouts = state.parsed.layouts.filter((layout) => layout.keys.length);
    if (!state.parsed.layoutMap[state.activeLayoutId]?.keys.length) {
      state.activeLayoutId = state.parsed.layoutMap.default?.keys.length
        ? "default"
        : layouts[0]?.id || "";
    }
    dom.layoutSelect.innerHTML = layouts.map((layout) => `
      <option value="${escapeHtml(layout.id)}"${layout.id === state.activeLayoutId ? " selected" : ""}>
        ${escapeHtml(layout.name || layout.id)} · ${escapeHtml(layout.id)}
      </option>
    `).join("");
  }

  function keyDisplayLabel(key) {
    if (key.label !== undefined) return String(key.label);
    const click = String(key.click ?? "");
    if (!click) return "";
    if (/^BackSpace/i.test(click)) return "⌫";
    if (/^Return/i.test(click)) return "↵";
    if (/^Shift/i.test(click)) return "⇧";
    if (/^Keyboard_number/i.test(click)) return "123";
    if (/^Keyboard_(bq|symbols)/i.test(click)) return "☺";
    if (/^Keyboard_/i.test(click)) return "键盘";
    if (/^space/i.test(click)) return "空格";
    if (/^Menu/i.test(click)) return "☰";
    if (/^Tab/i.test(click)) return "Tab";
    if (/^Left/i.test(click)) return "←";
    if (/^Right/i.test(click)) return "→";
    if (/^Up/i.test(click)) return "↑";
    if (/^Down/i.test(click)) return "↓";
    if (/^Home/i.test(click)) return "Home";
    if (/^End/i.test(click)) return "End";
    if (/^select_all/i.test(click)) return "全选";
    if (/^copy/i.test(click)) return "复制";
    if (/^paste/i.test(click)) return "粘贴";
    if (/^cut/i.test(click)) return "剪切";
    return click
      .replace(/\{Left\}/g, "")
      .replace(/^'|'$/g, "");
  }

  function keySymbolLabel(key) {
    const symbol = key.long_click ?? key.swipe_down ?? "";
    const raw = String(symbol);
    if (!raw || /^(select_all|copy|paste|paste_clip|cut|Menu|Theme_settings)$/i.test(raw)) {
      const labels = {
        select_all: "全选",
        copy: "复制",
        paste: "粘贴",
        paste_clip: "粘贴",
        cut: "剪切",
        Menu: "菜单",
        Theme_settings: "主题",
      };
      return labels[raw] || "";
    }
    return keyDisplayLabel({ click: raw });
  }

  function renderCandidates() {
    dom.candidateRow.innerHTML = CANDIDATES.map(([text, index], itemIndex) => `
      <div class="candidate${itemIndex === 0 ? " active" : ""}">
        <small>${index}</small><b>${text}</b>
      </div>
    `).join("") + '<div class="candidate more">›</div>';
  }

  function renderKeyboard() {
    const layout = getEffectiveLayout();
    if (!layout) {
      dom.keyboardPreview.innerHTML = '<div class="editor-empty">配置中没有可预览的键盘布局</div>';
      return;
    }
    const defaultWidth = Number(layout.width) || Number(state.parsed.style.key_width) || 10;
    const rows = [];
    let row = [];
    let used = 0;

    layout.keys.forEach((key, index) => {
      const width = Number(key.width) || defaultWidth;
      if (row.length && used + width > 100.5) {
        rows.push(row);
        row = [];
        used = 0;
      }
      row.push({ key, width, index });
      used += width;
      if (used >= 99.5) {
        rows.push(row);
        row = [];
        used = 0;
      }
    });
    if (row.length) rows.push(row);

    const id = state.activeThemeId;
    const editingLayout = state.inspectorMode === "layout";
    dom.keyboardPreview.innerHTML = rows.map((keys) => `
      <div class="key-row">
        ${keys.map(({ key, width, index }) => {
          const label = keyDisplayLabel(key);
          const symbol = keySymbolLabel(key);
          const background = resolveToken(id, key.key_back_color, "key_back_color");
          const foreground = resolveToken(id, key.key_text_color, "key_text_color");
          const symbolColor = resolveToken(id, key.key_symbol_color || key.key_text_color, "key_symbol_color");
          const activeBackground = resolveToken(id, key.hilited_key_back_color, "hilited_key_back_color");
          const activeForeground = resolveToken(id, key.hilited_key_text_color, "hilited_key_text_color");
          const border = resolveColor(id, "key_border_color");
          const shadow = resolveColor(id, "shadow_color");
          const spacer = !key.click && !key.label;
          const pressed = !editingLayout && state.previewState === "pressed" && (
            state.pressedKeyIndex >= 0 ? state.pressedKeyIndex === index : /^BackSpace/i.test(String(key.click ?? ""))
          );
          const selected = editingLayout && state.selectedKeyIndex === index;
          const wideLabel = label.length > 4 ? " wide-label" : "";
          return `
            <button
              class="trime-key${spacer ? " spacer" : ""}${pressed ? " preview-pressed" : ""}${wideLabel}${editingLayout ? " layout-editable" : ""}${selected ? " layout-selected" : ""}"
              type="button"
              data-key-index="${index}"
              draggable="${editingLayout ? "true" : "false"}"
              title="${escapeHtml(String(key.click ?? label))}"
              style="flex:0 0 calc(${Math.min(width, 100)}% - 4px);--key-bg:${colorToCss(background)};--key-fg:${colorToCss(foreground)};--key-symbol:${colorToCss(symbolColor)};--key-active-bg:${colorToCss(activeBackground)};--key-active-fg:${colorToCss(activeForeground)};--key-border:${colorToCss(border)};--key-shadow:${colorToCss({ ...shadow, a: Math.min(shadow.a, 48) })}"
              ${spacer && !editingLayout ? "tabindex=\"-1\"" : ""}
            >
              ${symbol ? `<small class="key-symbol">${escapeHtml(symbol)}</small>` : ""}
              <span>${escapeHtml(label)}</span>
              ${pressed ? `
                <span class="key-popup-demo" aria-hidden="true">
                  <i>${escapeHtml(symbol || label || "·")}</i>
                  <b>${escapeHtml(label || "·")}</b>
                </span>
              ` : ""}
            </button>
          `;
        }).join("")}
      </div>
    `).join("");
  }

  function renderPreview() {
    const id = state.activeThemeId;
    if (!id) return;
    const props = getEffectiveProps(id);
    dom.previewThemeName.textContent = props.name || id;
    dom.previewThemeId.textContent = id;
    dom.imePreview.classList.toggle("state-pressed", state.previewState === "pressed");

    const variables = {
      "--trime-root-bg": resolveColor(id, "root_background"),
      "--trime-text": resolveColor(id, "text_color"),
      "--trime-preedit-text": resolveColor(id, "hilited_text_color"),
      "--trime-preedit-bg": resolveColor(id, "text_back_color"),
      "--trime-candidate-bg": resolveColor(id, "candidate_background"),
      "--trime-candidate-text": resolveColor(id, "candidate_text_color"),
      "--trime-comment": resolveColor(id, "comment_text_color"),
      "--trime-candidate-separator": resolveColor(id, "candidate_separator_color"),
      "--trime-candidate-active-bg": resolveColor(id, "hilited_candidate_back_color"),
      "--trime-candidate-active-text": resolveColor(id, "hilited_candidate_text_color"),
      "--trime-candidate-button-active": resolveColor(id, "hilited_candidate_button_color"),
      "--trime-label-active": resolveColor(id, "hilited_label_color"),
      "--trime-comment-active": resolveColor(id, "hilited_comment_text_color"),
      "--trime-keyboard-bg": resolveColor(id, "keyboard_background"),
      "--trime-popup-bg": resolveColor(id, "popup_back_color"),
      "--trime-popup-text": resolveColor(id, "popup_text_color"),
      "--trime-popup-active-bg": resolveColor(id, "hilited_popup_back_color"),
      "--trime-popup-active-text": resolveColor(id, "hilited_popup_text_color"),
    };
    Object.entries(variables).forEach(([key, value]) => {
      dom.imePreview.style.setProperty(key, colorToCss(value));
    });

    renderCandidates();
    renderKeyboard();
    renderContrastInsights();
  }

  function renderContrastInsights() {
    const id = state.activeThemeId;
    const checks = [
      ["普通按键", "key_text_color", "key_back_color"],
      ["高亮候选", "hilited_candidate_text_color", "hilited_candidate_back_color"],
      ["候选文字", "candidate_text_color", "candidate_background"],
    ];
    dom.contrastInsights.innerHTML = checks.map(([label, foreground, background]) => {
      const ratio = contrastRatio(resolveColor(id, foreground), resolveColor(id, background));
      const warning = ratio < 4.5;
      return `
        <div class="contrast-chip${warning ? " warn" : ""}" title="${warning ? "小字号文字建议达到 4.5:1" : "对比度良好"}">
          <i></i><span>${label}</span><b>${ratio.toFixed(1)}:1</b>
        </div>
      `;
    }).join("");
  }

  function propertyMatchesSearch(key) {
    const query = state.colorSearch.trim().toLocaleLowerCase();
    if (!query) return true;
    return `${key} ${COLOR_LABELS[key] || ""}`.toLocaleLowerCase().includes(query);
  }

  function isColorProperty(key, value) {
    return OFFICIAL_COLOR_KEYS.has(key)
      || LEGACY_COLOR_KEYS.has(key)
      || key.endsWith("_color")
      || CUSTOM_COLOR_KEYS.has(key)
      || Boolean(parseColor(value));
  }

  function renderColorRow(themeId, key) {
    const props = getEffectiveProps(themeId);
    const explicit = hasOwn(props, key);
    const raw = explicit ? String(props[key]) : colorToArgb(resolveColor(themeId, key));
    const resolved = resolveColor(themeId, key);
    const imageValue = isImageValue(raw);
    const legacyReplacement = LEGACY_COLOR_KEYS.get(key);
    const alpha = imageValue ? "IMG" : resolved.a.toString(16).padStart(2, "0").toUpperCase();
    const label = COLOR_LABELS[key] || key.replace(/_/g, " ");
    return `
      <div class="color-row${explicit ? "" : " inherited"}" data-color-row="${escapeHtml(key)}">
        <label class="picker-wrap" style="--picker-color:${colorToCss(resolved)}" title="打开取色器">
          <span class="picker-color"></span>
          <input class="color-picker" data-color-key="${escapeHtml(key)}" type="color" value="${colorToRgbHex(resolved)}" aria-label="调整${escapeHtml(label)}">
        </label>
        <div class="color-row-main">
          <div class="color-row-head">
            <strong title="${escapeHtml(key)}">${escapeHtml(label)}</strong>
            ${explicit ? "" : '<span class="inherit-badge">继承</span>'}
            ${raw.startsWith("*") ? `<span class="anchor-badge">${escapeHtml(raw)}</span>` : ""}
            ${imageValue ? '<span class="image-badge">图片</span>' : ""}
            ${legacyReplacement ? `<span class="deprecated-badge" title="当前版本请改用 ${escapeHtml(legacyReplacement)}">已废弃 → ${escapeHtml(legacyReplacement)}</span>` : ""}
          </div>
          <div class="color-row-controls">
            <input
              class="color-value-input"
              data-color-key="${escapeHtml(key)}"
              value="${escapeHtml(raw)}"
              spellcheck="false"
              aria-label="${escapeHtml(label)} ARGB 值"
            >
            <span class="alpha-label">A <b>${alpha}</b></span>
          </div>
        </div>
      </div>
    `;
  }

  function renderColorEditor() {
    const id = state.activeThemeId;
    const props = getEffectiveProps(id);
    const canonical = OFFICIAL_COLOR_KEYS;
    const legacy = Object.keys(props).filter((key) => LEGACY_COLOR_KEYS.has(key));
    const custom = Object.keys(props).filter((key) => (
      !canonical.has(key)
      && !LEGACY_COLOR_KEYS.has(key)
      && isColorProperty(key, props[key])
      && key !== "name"
      && key !== "author"
    ));
    const groups = [
      ...COLOR_GROUPS,
      ...(legacy.length ? [{ title: "旧版字段（当前 Trime 已不再读取）", keys: legacy }] : []),
      ...(custom.length ? [{ title: "此主题的自定义键位", keys: custom }] : []),
    ];
    let visibleCount = 0;

    dom.colorEditor.innerHTML = groups.map((group) => {
      const keys = [...new Set(group.keys)].filter(propertyMatchesSearch);
      if (!keys.length) return "";
      visibleCount += keys.length;
      return `
        <section class="color-group">
          <h3 class="color-group-title">${escapeHtml(group.title)} <span>${keys.length}</span></h3>
          ${keys.map((key) => renderColorRow(id, key)).join("")}
        </section>
      `;
    }).join("");
    dom.colorEmpty.hidden = visibleCount > 0;
  }

  function keyPosition(layout, targetIndex) {
    const defaultWidth = Number(layout.width) || Number(state.parsed.style.key_width) || 10;
    let row = 1;
    let column = 0;
    let used = 0;
    for (let index = 0; index <= targetIndex; index += 1) {
      const width = Number(layout.keys[index]?.width) || defaultWidth;
      if (column > 0 && used + width > 100.5) {
        row += 1;
        column = 0;
        used = 0;
      }
      column += 1;
      used += width;
      if (index === targetIndex) return { row, column };
      if (used >= 99.5) {
        row += 1;
        column = 0;
        used = 0;
      }
    }
    return { row: 1, column: 1 };
  }

  function renderLayoutEditor() {
    const layout = getEffectiveLayout();
    if (!layout) return;
    state.selectedKeyIndex = Math.min(state.selectedKeyIndex, Math.max(0, layout.keys.length - 1));
    const key = layout.keys[state.selectedKeyIndex] || null;
    const label = key ? keyDisplayLabel(key) || "空白占位键" : "没有按键";
    const position = key ? keyPosition(layout, state.selectedKeyIndex) : null;

    dom.layoutEditorName.textContent = `${layout.name || layout.id} · ${layout.id}`;
    dom.layoutKeyCount.textContent = `${layout.keys.length} 键`;
    dom.selectedKeyTitle.textContent = label;
    dom.selectedKeyPosition.textContent = key
      ? `#${state.selectedKeyIndex + 1} · 第 ${position.row} 行`
      : "—";

    document.querySelectorAll("[data-key-prop]").forEach((input) => {
      const prop = input.dataset.keyProp;
      input.disabled = !key;
      if (document.activeElement === input) return;
      if (!key) input.value = "";
      else if (prop === "width") input.value = key.width || layout.width || state.parsed.style.key_width || 10;
      else input.value = key[prop] ?? "";
    });

    document.querySelectorAll("[data-key-move]").forEach((button) => {
      const offset = Number(button.dataset.keyMove);
      button.disabled = !key
        || state.selectedKeyIndex + offset < 0
        || state.selectedKeyIndex + offset >= layout.keys.length;
    });
    dom.deleteKeyButton.disabled = !key;
    dom.resetLayoutButton.disabled = !isLayoutChanged(layout.id);

    dom.keyOrderList.innerHTML = layout.keys.map((item, index) => `
      <button
        class="key-order-item${index === state.selectedKeyIndex ? " active" : ""}"
        type="button"
        data-order-key-index="${index}"
        title="#${index + 1} ${escapeHtml(String(item.click ?? item.label ?? "空白占位键"))}"
      >${escapeHtml(keyDisplayLabel(item) || "□")}</button>
    `).join("");
  }

  function renderInspectorMode() {
    const layoutMode = state.inspectorMode === "layout";
    dom.inspectorTitle.textContent = layoutMode ? "键盘布局编辑器" : "颜色编辑器";
    dom.colorInspectorHead.hidden = layoutMode;
    dom.colorInspectorBody.hidden = layoutMode;
    dom.layoutInspectorHead.hidden = !layoutMode;
    dom.layoutInspectorBody.hidden = !layoutMode;
    document.querySelectorAll("[data-inspector-mode]").forEach((button) => {
      button.classList.toggle("active", button.dataset.inspectorMode === state.inspectorMode);
    });
    if (layoutMode) renderLayoutEditor();
  }

  function renderEditorMeta() {
    const props = getEffectiveProps(state.activeThemeId);
    if (document.activeElement !== dom.themeNameInput) dom.themeNameInput.value = props.name || "";
    if (document.activeElement !== dom.themeAuthorInput) dom.themeAuthorInput.value = props.author || "";
    const dirty = state.inspectorMode === "layout"
      ? isLayoutChanged(state.activeLayoutId)
      : isThemeChanged(state.activeThemeId);
    dom.dirtyBadge.hidden = !dirty;
    dom.dirtyBadge.textContent = state.inspectorMode === "layout" ? "布局已修改" : "已修改";
  }

  function renderStatus() {
    const count = changedThemeCount();
    const values = changedValueCount();
    const layouts = changedLayoutCount();
    dom.sourceName.textContent = state.sourceName;
    const changeParts = [
      values ? `${values} 处颜色修改` : "",
      layouts ? `${layouts} 个布局修改` : "",
    ].filter(Boolean);
    dom.sourceMeta.textContent = `${state.parsed.themes.length} 个主题 · ${state.parsed.layouts.filter((layout) => layout.keys.length).length} 种布局${changeParts.length ? ` · ${changeParts.join(" · ")}` : ""}`;
    dom.undoButton.disabled = state.undoStack.length === 0;
    dom.exportSummary.textContent = values || layouts
      ? `已修改 ${count} 个主题（${values} 个值）和 ${layouts} 个键盘布局`
      : "尚未修改任何内容";
    dom.resetAllButton.hidden = values === 0 && layouts === 0;
  }

  function renderAll() {
    renderStatus();
    renderThemeList();
    renderLayoutSelect();
    renderPreview();
    renderEditorMeta();
    renderColorEditor();
    renderInspectorMode();
  }

  function beginFieldEdit(element, themeId, key) {
    element.dataset.beforeToken = JSON.stringify(currentChangeToken(themeId, key));
  }

  function finishFieldEdit(element, themeId, key) {
    historyFromElement(element, themeId, key);
    delete element.dataset.beforeToken;
    renderAll();
  }

  function formatScalar(value, meta, key) {
    const stringValue = String(value ?? "");
    if (isColorProperty(key, stringValue) || key === "dark_scheme" || key === "light_scheme") {
      return stringValue;
    }
    if (meta?.quote === "single") return `'${stringValue.replace(/'/g, "''")}'`;
    if (meta?.quote === "double") return JSON.stringify(stringValue);
    if (
      !stringValue
      || /^\s|\s$/.test(stringValue)
      || /[:#[\]{},&*!|>'"%@`]/.test(stringValue)
      || /^(true|false|null|~|[-+]?[0-9.]+)$/i.test(stringValue)
    ) {
      return JSON.stringify(stringValue);
    }
    return stringValue;
  }

  function formatInlineYamlValue(value) {
    const stringValue = String(value ?? "");
    if (/^\*[A-Za-z0-9_.-]+$/.test(stringValue) && hasOwn(state.parsed.anchors, stringValue.slice(1))) {
      return stringValue;
    }
    if (/^(?:true|false|null|~)$/i.test(stringValue)) return stringValue.toLowerCase();
    if (/^[-+]?(?:\d+\.?\d*|\.\d+)$/.test(stringValue)) return stringValue;
    if (/^[A-Za-z_][A-Za-z0-9_.\/-]*$/.test(stringValue)) return stringValue;
    return `'${stringValue.replace(/'/g, "''")}'`;
  }

  function serializeInlineKey(key) {
    return Object.entries(key)
      .filter(([name]) => name)
      .map(([name, value]) => `${name}: ${formatInlineYamlValue(value)}`)
      .join(", ");
  }

  function buildFullYaml() {
    const replacements = new Map();
    const insertions = new Map();
    const layoutInsertions = new Map();
    const skippedLayoutLines = new Set();

    Object.entries(state.changes).forEach(([themeId, changes]) => {
      const theme = state.parsed.themeMap[themeId];
      if (!theme) return;
      Object.entries(changes).forEach(([key, value]) => {
        const meta = theme.propertyMeta[key];
        if (meta) {
          replacements.set(
            meta.lineIndex,
            `${meta.prefix}${formatScalar(value, meta, key)}${meta.comment}`,
          );
        } else {
          const list = insertions.get(theme.lastPropertyLine) || [];
          list.push(`    ${key}: ${formatScalar(value, null, key)}`);
          insertions.set(theme.lastPropertyLine, list);
        }
      });
    });

    Object.entries(state.layoutChanges).forEach(([layoutId, keys]) => {
      const layout = state.parsed.layoutMap[layoutId];
      if (!layout || layout.keysHeaderLine < 0) return;
      layout.keyLineIndices.forEach((lineIndex) => skippedLayoutLines.add(lineIndex));
      layoutInsertions.set(
        layout.keysHeaderLine,
        keys.map((key) => `${layout.keyIndent}- {${serializeInlineKey(key)}}`),
      );
    });

    const output = [];
    state.parsed.lines.forEach((line, index) => {
      if (skippedLayoutLines.has(index)) return;
      output.push(replacements.get(index) ?? line);
      const additions = insertions.get(index);
      if (additions) output.push(...additions);
      const layoutKeys = layoutInsertions.get(index);
      if (layoutKeys) output.push(...layoutKeys);
    });
    let result = output.join(state.parsed.eol);
    if (!state.parsed.hasFinalEol) result = result.replace(/\r?\n$/, "");
    return result;
  }

  function buildSchemeYaml() {
    const theme = state.parsed.themeMap[state.activeThemeId];
    const changes = state.changes[state.activeThemeId] || {};
    const lines = [
      "preset_color_schemes:",
      `  ${theme.id}:`,
    ];
    theme.propertyOrder.forEach((key) => {
      const meta = theme.propertyMeta[key];
      const value = hasOwn(changes, key) ? changes[key] : theme.props[key];
      lines.push(`    ${key}: ${formatScalar(value, meta, key)}${meta.comment}`);
    });
    Object.entries(changes).forEach(([key, value]) => {
      if (!theme.propertyMeta[key]) lines.push(`    ${key}: ${formatScalar(value, null, key)}`);
    });
    return `${lines.join("\n")}\n`;
  }

  function editedFilename() {
    if (/\.trime\.ya?ml$/i.test(state.sourceName)) {
      return state.sourceName.replace(/\.trime\.ya?ml$/i, ".edited.trime.yaml");
    }
    if (/\.ya?ml$/i.test(state.sourceName)) {
      return state.sourceName.replace(/\.ya?ml$/i, ".edited.yaml");
    }
    return `${state.sourceName}.edited.trime.yaml`;
  }

  function downloadText(text, filename) {
    const blob = new Blob([text], { type: "text/yaml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.append(textarea);
      textarea.select();
      document.execCommand("copy");
      textarea.remove();
    }
  }

  function toast(message) {
    dom.toast.textContent = message;
    dom.toast.classList.add("show");
    clearTimeout(state.toastTimer);
    state.toastTimer = window.setTimeout(() => dom.toast.classList.remove("show"), 1900);
  }

  function isAndroidHost() {
    return typeof window.AndroidRime !== "undefined";
  }

  function setAndroidStatus(message) {
    if (dom.androidFileStatus) dom.androidFileStatus.textContent = message;
  }

  function callAndroid(method, ...args) {
    if (!isAndroidHost() || typeof window.AndroidRime[method] !== "function") {
      toast("当前环境不支持直接访问安卓文件");
      return;
    }
    try {
      window.AndroidRime[method](...args);
    } catch (error) {
      toast(`安卓文件操作失败：${error?.message || "未知错误"}`);
    }
  }

  function renderAndroidFileList() {
    if (!dom.androidFileSelect) return;
    dom.androidFileSelect.hidden = !state.androidFiles.length;
    dom.androidFileSelect.innerHTML = state.androidFiles.map((file) => (
      `<option value="${escapeHtml(file.uri)}">${escapeHtml(file.name)}</option>`
    )).join("");
  }

  function setupAndroidBridge() {
    if (!isAndroidHost()) return;
    document.body.classList.add("android-host");
    dom.androidRimeBar.hidden = false;
    setAndroidStatus("可打开 Rime 配置或授权整个文件夹");

    window.TrimeAndroidBridge = {
      receiveFile(name, text, uri) {
        if (loadConfig(String(text || ""), name || "trime.yaml")) {
          state.androidFileUri = uri || "";
          setAndroidStatus(state.androidFileUri ? `已打开 ${state.sourceName}` : "已打开配置");
          toast(`已载入 ${state.sourceName}`);
        }
      },
      receiveFileList(payload, preferredUri) {
        try {
          const files = typeof payload === "string" ? JSON.parse(payload) : payload;
          state.androidFiles = Array.isArray(files) ? files : [];
          renderAndroidFileList();
          if (state.androidFiles.length) {
            const preferred = state.androidFiles.some((file) => file.uri === preferredUri)
              ? preferredUri
              : state.androidFiles[0].uri;
            setAndroidStatus(`已找到 ${state.androidFiles.length} 个 YAML 配置`);
            dom.androidFileSelect.value = preferred;
            state.androidFileUri = preferred;
            callAndroid("openTreeFile", preferred);
          } else {
            setAndroidStatus("这个文件夹里没有找到 YAML 配置");
          }
        } catch {
          toast("读取文件夹列表失败");
        }
      },
      notify(message) {
        setAndroidStatus(String(message || ""));
        toast(String(message || "操作完成"));
      },
    };
  }

  function loadConfig(text, filename) {
    const parsed = parseTrimeYaml(text);
    if (!parsed.themes.length) {
      toast("没有找到 preset_color_schemes，未载入文件");
      return false;
    }
    state.sourceText = text;
    state.sourceName = filename || "trime.yaml";
    state.parsed = parsed;
    state.changes = {};
    state.layoutChanges = {};
    state.undoStack = [];
    state.redoStack = [];
    state.activeThemeId = parsed.themeMap.default ? "default" : parsed.themes[0].id;
    state.activeLayoutId = parsed.layoutMap.default?.keys.length
      ? "default"
      : parsed.layouts.find((layout) => layout.keys.length)?.id || "";
    state.themeSearch = "";
    state.colorSearch = "";
    state.themeFilter = "all";
    state.inspectorMode = "color";
    state.selectedKeyIndex = 0;
    state.draggedKeyIndex = -1;
    state.previewState = "normal";
    state.pressedKeyIndex = -1;
    dom.themeSearch.value = "";
    dom.colorSearch.value = "";
    document.querySelectorAll(".filter-tab").forEach((button) => {
      button.classList.toggle("active", button.dataset.filter === "all");
    });
    renderAll();
    return true;
  }

  function readFile(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (loadConfig(String(reader.result), file.name)) {
        toast(`已载入 ${file.name}`);
      }
    };
    reader.onerror = () => toast("读取文件失败，请确认文件可访问");
    reader.readAsText(file, "utf-8");
  }

  function resetCurrentTheme() {
    const id = state.activeThemeId;
    const changes = state.changes[id] || {};
    const items = Object.keys(changes).map((key) => ({
      themeId: id,
      key,
      before: { present: true, value: changes[key] },
      after: { present: false, value: "" },
    }));
    if (!items.length) {
      toast("当前主题还没有修改");
      return;
    }
    items.forEach((item) => setChangeToken(item.themeId, item.key, item.after));
    pushHistory(items);
    renderAll();
    toast("已重置当前主题");
  }

  function addKey() {
    const layout = getEffectiveLayout();
    if (!layout) return;
    const before = cloneKeys(layout.keys);
    const insertIndex = layout.keys.length
      ? Math.min(state.selectedKeyIndex + 1, layout.keys.length)
      : 0;
    const defaultWidth = String(Number(layout.width) || Number(state.parsed.style.key_width) || 10);
    const after = cloneKeys(before);
    after.splice(insertIndex, 0, { click: "新按键", width: defaultWidth });
    state.selectedKeyIndex = insertIndex;
    setLayoutKeys(layout.id, after);
    pushLayoutHistory(layout.id, before, after);
    renderAll();
    toast("已新增一个按键");
  }

  function deleteSelectedKey() {
    const layout = getEffectiveLayout();
    if (!layout?.keys[state.selectedKeyIndex]) return;
    const before = cloneKeys(layout.keys);
    const after = cloneKeys(before);
    after.splice(state.selectedKeyIndex, 1);
    state.selectedKeyIndex = Math.min(state.selectedKeyIndex, Math.max(0, after.length - 1));
    setLayoutKeys(layout.id, after);
    pushLayoutHistory(layout.id, before, after);
    renderAll();
    toast("已删除所选按键");
  }

  function moveSelectedKey(offset) {
    const layout = getEffectiveLayout();
    if (!layout) return;
    const sourceIndex = state.selectedKeyIndex;
    const targetIndex = sourceIndex + offset;
    if (sourceIndex < 0 || targetIndex < 0 || targetIndex >= layout.keys.length) return;
    const before = cloneKeys(layout.keys);
    const after = cloneKeys(before);
    const [key] = after.splice(sourceIndex, 1);
    after.splice(targetIndex, 0, key);
    state.selectedKeyIndex = targetIndex;
    setLayoutKeys(layout.id, after);
    pushLayoutHistory(layout.id, before, after);
    renderAll();
  }

  function resetCurrentLayout() {
    const layout = getEffectiveLayout();
    const original = state.parsed.layoutMap[state.activeLayoutId];
    if (!layout || !original || !isLayoutChanged(layout.id)) {
      toast("当前布局还没有修改");
      return;
    }
    const before = cloneKeys(layout.keys);
    const after = cloneKeys(original.keys);
    setLayoutKeys(layout.id, after);
    state.selectedKeyIndex = Math.min(state.selectedKeyIndex, Math.max(0, after.length - 1));
    pushLayoutHistory(layout.id, before, after);
    renderAll();
    toast("已重置当前键盘布局");
  }

  function beginKeyFieldEdit(input) {
    if (input.dataset.beforeKeys) return;
    const layout = getEffectiveLayout();
    if (!layout) return;
    input.dataset.beforeKeys = JSON.stringify(layout.keys);
    input.dataset.layoutId = layout.id;
    input.dataset.keyIndex = String(state.selectedKeyIndex);
  }

  function updateKeyField(input) {
    const layoutId = input.dataset.layoutId || state.activeLayoutId;
    const keyIndex = Number(input.dataset.keyIndex ?? state.selectedKeyIndex);
    const layout = getEffectiveLayout(layoutId);
    if (!layout?.keys[keyIndex]) return false;
    const prop = input.dataset.keyProp;
    const value = input.value.trim();
    if (prop === "width" && value && (!Number.isFinite(Number(value)) || Number(value) <= 0 || Number(value) > 100)) {
      input.classList.add("invalid");
      return false;
    }
    input.classList.remove("invalid");
    const keys = cloneKeys(layout.keys);
    if (value) keys[keyIndex][prop] = value;
    else delete keys[keyIndex][prop];
    setLayoutKeys(layoutId, keys);
    renderKeyboard();
    renderStatus();
    renderEditorMeta();
    renderLayoutEditor();
    return true;
  }

  function finishKeyFieldEdit(input) {
    const layoutId = input.dataset.layoutId || state.activeLayoutId;
    let before = [];
    try {
      before = JSON.parse(input.dataset.beforeKeys || "[]");
    } catch {
      before = cloneKeys(state.parsed.layoutMap[layoutId]?.keys || []);
    }
    if (!updateKeyField(input)) {
      setLayoutKeys(layoutId, before);
      input.classList.remove("invalid");
      delete input.dataset.beforeKeys;
      delete input.dataset.layoutId;
      delete input.dataset.keyIndex;
      renderAll();
      toast("宽度需为大于 0 且不超过 100 的数字");
      return;
    }
    const after = getEffectiveLayout(layoutId)?.keys || [];
    pushLayoutHistory(layoutId, before, after);
    delete input.dataset.beforeKeys;
    delete input.dataset.layoutId;
    delete input.dataset.keyIndex;
    renderAll();
  }

  function clearKeyDropIndicators() {
    dom.keyboardPreview.querySelectorAll(".dragging, .drop-before, .drop-after").forEach((key) => {
      key.classList.remove("dragging", "drop-before", "drop-after");
    });
  }

  function reorderDraggedKey(targetIndex, afterTarget) {
    const layout = getEffectiveLayout();
    const sourceIndex = state.draggedKeyIndex;
    if (!layout || sourceIndex < 0 || targetIndex < 0 || targetIndex >= layout.keys.length) return;
    const before = cloneKeys(layout.keys);
    const after = cloneKeys(before);
    const [key] = after.splice(sourceIndex, 1);
    let insertIndex = targetIndex + (afterTarget ? 1 : 0);
    if (sourceIndex < insertIndex) insertIndex -= 1;
    insertIndex = Math.max(0, Math.min(insertIndex, after.length));
    after.splice(insertIndex, 0, key);
    state.selectedKeyIndex = insertIndex;
    setLayoutKeys(layout.id, after);
    pushLayoutHistory(layout.id, before, after);
  }

  function resetAllThemes() {
    const themeItems = [];
    Object.entries(state.changes).forEach(([themeId, changes]) => {
      Object.entries(changes).forEach(([key, value]) => {
        themeItems.push({
          themeId,
          key,
          before: { present: true, value },
          after: { present: false, value: "" },
        });
      });
    });
    const layoutItems = Object.entries(state.layoutChanges).map(([layoutId, keys]) => ({
      layoutId,
      before: cloneKeys(keys),
      after: cloneKeys(state.parsed.layoutMap[layoutId]?.keys || []),
    }));
    if (!themeItems.length && !layoutItems.length) return;
    themeItems.forEach((item) => setChangeToken(item.themeId, item.key, item.after));
    layoutItems.forEach((item) => setLayoutKeys(item.layoutId, item.after));
    state.undoStack.push({ type: "reset-all", themeItems, layoutItems });
    if (state.undoStack.length > 100) state.undoStack.shift();
    state.redoStack = [];
    dom.exportDialog.close();
    renderAll();
    toast("已放弃全部修改，可使用撤销恢复");
  }

  function bindEvents() {
    dom.themeList.addEventListener("click", (event) => {
      const card = event.target.closest("[data-theme-id]");
      if (!card) return;
      state.activeThemeId = card.dataset.themeId;
      state.pressedKeyIndex = -1;
      renderAll();
    });

    dom.themeSearch.addEventListener("input", () => {
      state.themeSearch = dom.themeSearch.value;
      renderThemeList();
    });

    dom.colorSearch.addEventListener("input", () => {
      state.colorSearch = dom.colorSearch.value;
      renderColorEditor();
    });

    dom.themeFilters.addEventListener("click", (event) => {
      const button = event.target.closest("[data-filter]");
      if (!button) return;
      state.themeFilter = button.dataset.filter;
      dom.themeFilters.querySelectorAll("[data-filter]").forEach((item) => {
        item.classList.toggle("active", item === button);
      });
      renderThemeList();
    });

    dom.clearSearchButton.addEventListener("click", () => {
      state.themeSearch = "";
      state.themeFilter = "all";
      dom.themeSearch.value = "";
      dom.themeFilters.querySelectorAll("[data-filter]").forEach((item) => {
        item.classList.toggle("active", item.dataset.filter === "all");
      });
      renderThemeList();
    });

    document.querySelectorAll("[data-inspector-mode]").forEach((button) => {
      button.addEventListener("click", () => {
        state.inspectorMode = button.dataset.inspectorMode;
        if (state.inspectorMode === "layout") {
          state.previewState = "normal";
          state.pressedKeyIndex = -1;
          document.querySelectorAll("[data-preview-state]").forEach((item) => {
            item.classList.toggle("active", item.dataset.previewState === "normal");
          });
        }
        renderAll();
      });
    });

    dom.layoutSelect.addEventListener("change", () => {
      state.activeLayoutId = dom.layoutSelect.value;
      state.pressedKeyIndex = -1;
      state.selectedKeyIndex = 0;
      renderAll();
    });

    document.querySelectorAll("[data-preview-state]").forEach((button) => {
      button.addEventListener("click", () => {
        state.previewState = button.dataset.previewState;
        document.querySelectorAll("[data-preview-state]").forEach((item) => {
          item.classList.toggle("active", item === button);
        });
        renderKeyboard();
      });
    });

    dom.keyboardPreview.addEventListener("click", (event) => {
      const key = event.target.closest("[data-key-index]");
      if (!key) return;
      if (state.inspectorMode === "layout") {
        state.selectedKeyIndex = Number(key.dataset.keyIndex);
        renderKeyboard();
        renderLayoutEditor();
        renderEditorMeta();
        return;
      }
      if (key.classList.contains("spacer")) return;
      state.previewState = "pressed";
      state.pressedKeyIndex = Number(key.dataset.keyIndex);
      document.querySelectorAll("[data-preview-state]").forEach((item) => {
        item.classList.toggle("active", item.dataset.previewState === "pressed");
      });
      renderKeyboard();
    });

    dom.keyboardPreview.addEventListener("dragstart", (event) => {
      if (state.inspectorMode !== "layout") return;
      const key = event.target.closest("[data-key-index]");
      if (!key) return;
      state.draggedKeyIndex = Number(key.dataset.keyIndex);
      state.selectedKeyIndex = state.draggedKeyIndex;
      key.classList.add("dragging");
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", String(state.draggedKeyIndex));
    });

    dom.keyboardPreview.addEventListener("dragover", (event) => {
      if (state.inspectorMode !== "layout" || state.draggedKeyIndex < 0) return;
      const key = event.target.closest("[data-key-index]");
      if (!key) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
      dom.keyboardPreview.querySelectorAll(".drop-before, .drop-after").forEach((item) => {
        item.classList.remove("drop-before", "drop-after");
      });
      const bounds = key.getBoundingClientRect();
      const afterTarget = event.clientX >= bounds.left + bounds.width / 2;
      key.classList.add(afterTarget ? "drop-after" : "drop-before");
    });

    dom.keyboardPreview.addEventListener("drop", (event) => {
      if (state.inspectorMode !== "layout" || state.draggedKeyIndex < 0) return;
      const key = event.target.closest("[data-key-index]");
      if (!key) return;
      event.preventDefault();
      const targetIndex = Number(key.dataset.keyIndex);
      const afterTarget = key.classList.contains("drop-after");
      reorderDraggedKey(targetIndex, afterTarget);
      state.draggedKeyIndex = -1;
      clearKeyDropIndicators();
      renderAll();
    });

    dom.keyboardPreview.addEventListener("dragend", () => {
      state.draggedKeyIndex = -1;
      clearKeyDropIndicators();
    });

    dom.keyOrderList.addEventListener("click", (event) => {
      const key = event.target.closest("[data-order-key-index]");
      if (!key) return;
      state.selectedKeyIndex = Number(key.dataset.orderKeyIndex);
      renderKeyboard();
      renderLayoutEditor();
      renderEditorMeta();
    });

    dom.addKeyButton.addEventListener("click", addKey);
    dom.deleteKeyButton.addEventListener("click", deleteSelectedKey);
    dom.resetLayoutButton.addEventListener("click", resetCurrentLayout);
    document.querySelectorAll("[data-key-move]").forEach((button) => {
      button.addEventListener("click", () => moveSelectedKey(Number(button.dataset.keyMove)));
    });

    dom.layoutInspectorBody.addEventListener("focusin", (event) => {
      const input = event.target.closest("[data-key-prop]");
      if (input) beginKeyFieldEdit(input);
    });

    dom.layoutInspectorBody.addEventListener("input", (event) => {
      const input = event.target.closest("[data-key-prop]");
      if (input) updateKeyField(input);
    });

    dom.layoutInspectorBody.addEventListener("change", (event) => {
      const input = event.target.closest("[data-key-prop]");
      if (input) finishKeyFieldEdit(input);
    });

    [
      [dom.themeNameInput, "name"],
      [dom.themeAuthorInput, "author"],
    ].forEach(([input, key]) => {
      input.addEventListener("focus", () => beginFieldEdit(input, state.activeThemeId, key));
      input.addEventListener("input", () => {
        setLiveValue(state.activeThemeId, key, input.value);
        renderPreview();
        renderThemeList();
        renderStatus();
        renderEditorMeta();
      });
      input.addEventListener("change", () => finishFieldEdit(input, state.activeThemeId, key));
    });

    dom.colorEditor.addEventListener("focusin", (event) => {
      const input = event.target.closest("[data-color-key]");
      if (!input || input.dataset.beforeToken) return;
      beginFieldEdit(input, state.activeThemeId, input.dataset.colorKey);
    });

    dom.colorEditor.addEventListener("pointerdown", (event) => {
      const input = event.target.closest("[data-color-key]");
      if (!input || input.dataset.beforeToken) return;
      beginFieldEdit(input, state.activeThemeId, input.dataset.colorKey);
    });

    dom.colorEditor.addEventListener("input", (event) => {
      const input = event.target;
      const key = input.dataset.colorKey;
      if (!key) return;
      const row = input.closest(".color-row");
      let value = "";

      if (input.classList.contains("color-picker")) {
        const current = resolveColor(state.activeThemeId, key);
        value = colorToArgb(withRgb(current, input.value));
        const textInput = row.querySelector(".color-value-input");
        textInput.value = value;
        textInput.classList.remove("invalid");
      } else if (input.classList.contains("color-value-input")) {
        const normalized = normalizeColorValue(input.value, key);
        if (!normalized) {
          input.classList.add("invalid");
          return;
        }
        input.classList.remove("invalid");
        value = normalized;
      } else {
        return;
      }

      setLiveValue(state.activeThemeId, key, value);
      const resolved = resolveColor(state.activeThemeId, key);
      row.querySelector(".picker-wrap").style.setProperty("--picker-color", colorToCss(resolved));
      row.querySelector(".color-picker").value = colorToRgbHex(resolved);
      row.querySelector(".alpha-label b").textContent = isImageValue(value)
        ? "IMG"
        : resolved.a.toString(16).padStart(2, "0").toUpperCase();
      renderPreview();
      renderThemeList();
      renderStatus();
      renderEditorMeta();
    });

    dom.colorEditor.addEventListener("change", (event) => {
      const input = event.target.closest("[data-color-key]");
      if (!input) return;
      const key = input.dataset.colorKey;
      if (input.classList.contains("color-value-input")) {
        const normalized = normalizeColorValue(input.value, key);
        if (!normalized) {
          input.value = hasOwn(getEffectiveProps(state.activeThemeId), key)
            ? String(getEffectiveProps(state.activeThemeId)[key])
            : colorToArgb(resolveColor(state.activeThemeId, key));
          input.classList.remove("invalid");
          toast("颜色格式无效，已恢复上一个值");
        } else {
          input.value = normalized;
          setLiveValue(state.activeThemeId, key, normalized);
        }
      }
      finishFieldEdit(input, state.activeThemeId, key);
    });

    dom.resetThemeButton.addEventListener("click", resetCurrentTheme);
    dom.undoButton.addEventListener("click", undo);

    dom.androidOpenFileButton.addEventListener("click", () => {
      callAndroid("openFile");
    });

    dom.androidOpenFolderButton.addEventListener("click", () => {
      callAndroid("openFolder");
    });

    dom.androidFileSelect.addEventListener("change", () => {
      state.androidFileUri = dom.androidFileSelect.value;
      callAndroid("openTreeFile", state.androidFileUri);
    });

    dom.androidSaveButton.addEventListener("click", () => {
      const yaml = buildFullYaml();
      if (state.androidFileUri) callAndroid("saveFile", yaml);
      else callAndroid("saveAsFile", editedFilename(), yaml);
    });

    dom.androidSaveAsButton.addEventListener("click", () => {
      callAndroid("saveAsFile", editedFilename(), buildFullYaml());
    });

    dom.importButton.addEventListener("click", () => dom.fileInput.click());
    dom.fileInput.addEventListener("change", () => {
      readFile(dom.fileInput.files?.[0]);
      dom.fileInput.value = "";
    });

    let dragDepth = 0;
    window.addEventListener("dragenter", (event) => {
      if (!event.dataTransfer?.types?.includes("Files")) return;
      event.preventDefault();
      dragDepth += 1;
      dom.dropOverlay.hidden = false;
    });
    window.addEventListener("dragover", (event) => {
      if (!event.dataTransfer?.types?.includes("Files")) return;
      event.preventDefault();
    });
    window.addEventListener("dragleave", (event) => {
      if (!event.dataTransfer?.types?.includes("Files")) return;
      dragDepth -= 1;
      if (dragDepth <= 0) {
        dragDepth = 0;
        dom.dropOverlay.hidden = true;
      }
    });
    window.addEventListener("drop", (event) => {
      if (!event.dataTransfer?.files?.length) return;
      event.preventDefault();
      dragDepth = 0;
      dom.dropOverlay.hidden = true;
      readFile(event.dataTransfer.files[0]);
    });

    dom.exportButton.addEventListener("click", () => {
      renderStatus();
      if (typeof dom.exportDialog.showModal === "function") dom.exportDialog.showModal();
      else dom.exportDialog.setAttribute("open", "");
    });

    dom.downloadFullButton.addEventListener("click", () => {
      downloadText(buildFullYaml(), editedFilename());
      toast(`已下载 ${editedFilename()}`);
      dom.exportDialog.close();
    });

    dom.copySchemeButton.addEventListener("click", async () => {
      await copyText(buildSchemeYaml());
      toast("当前主题 YAML 已复制");
      dom.exportDialog.close();
    });

    dom.downloadSchemeButton.addEventListener("click", () => {
      downloadText(buildSchemeYaml(), `${state.activeThemeId}.color-scheme.yaml`);
      toast("当前主题片段已下载");
      dom.exportDialog.close();
    });

    dom.resetAllButton.addEventListener("click", resetAllThemes);

    window.addEventListener("keydown", (event) => {
      const modifier = event.ctrlKey || event.metaKey;
      if (event.key === "/" && !modifier) {
        const tag = document.activeElement?.tagName;
        if (!["INPUT", "TEXTAREA", "SELECT"].includes(tag)) {
          event.preventDefault();
          dom.themeSearch.focus();
        }
      }
      if (modifier && event.key.toLowerCase() === "z") {
        event.preventDefault();
        if (event.shiftKey) redo();
        else undo();
      }
      if (modifier && event.key.toLowerCase() === "y") {
        event.preventDefault();
        redo();
      }
    });
  }

  function initialize() {
    cacheDom();
    setupAndroidBridge();
    bindEvents();
    if (!window.BUNDLED_TRIME_YAML) {
      dom.sourceMeta.textContent = "缺少 bundled-config.js";
      toast("未找到内置配置，请先运行构建脚本");
      return;
    }
    loadConfig(window.BUNDLED_TRIME_YAML, "trime.yaml");
  }

  document.addEventListener("DOMContentLoaded", initialize);
})();
