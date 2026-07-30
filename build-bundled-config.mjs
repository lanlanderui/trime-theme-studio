import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { basename, resolve } from "node:path";

const root = fileURLToPath(new URL(".", import.meta.url));
const sourcePath = resolve(root, process.argv[2] || "trime.yaml");
const outputPath = resolve(root, "bundled-config.js");
const yaml = readFileSync(sourcePath, "utf8");
const sourceName = basename(sourcePath);

writeFileSync(
  outputPath,
  `/* Generated from ${sourceName}. Run: node build-bundled-config.mjs */\nwindow.BUNDLED_TRIME_YAML = ${JSON.stringify(yaml)};\n`,
  "utf8",
);

console.log(`Bundled ${yaml.length.toLocaleString()} characters from ${sourceName} into bundled-config.js`);
