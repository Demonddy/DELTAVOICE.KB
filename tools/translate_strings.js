/**
 * Translates values/strings.xml into target values-* folders using Google Translate (unofficial API).
 * Preserves Android format placeholders (%s, %1$d, %%), brand strings, and XML escapes.
 *
 * Usage: node translate_strings.js
 * Requires: npm install @vitalets/google-translate-api (in tools/)
 */

const fs = require("fs");
const path = require("path");
const { translate } = require("@vitalets/google-translate-api");

const REPO_ROOT = path.join(__dirname, "..");
const DEFAULT_STRINGS = path.join(REPO_ROOT, "app", "src", "main", "res", "values", "strings.xml");

/** Folders that were byte-identical to default (need full MT) */
const TARGET_FOLDERS = [
  "values-da",
  "values-el",
  "values-fa",
  "values-fi",
  "values-ga",
  "values-ha",
  "values-hr",
  "values-id",
  "values-it",
  "values-ja",
  "values-kk",
  "values-kn",
  "values-ko",
  "values-lo",
  "values-lt",
  "values-lv",
  "values-mk",
  "values-ml",
  "values-mr",
  "values-ms",
  "values-nb",
  "values-nl",
  "values-om",
  "values-pa",
  "values-pl",
  "values-pt-rBR",
  "values-pt-rPT",
  "values-ro",
  "values-ru",
  "values-sk",
  "values-sl",
  "values-sq",
  "values-sr",
  "values-sv",
  "values-sw",
  "values-ta",
  "values-te",
  "values-th",
  "values-tr",
  "values-uk",
  "values-ur",
  "values-uz",
  "values-vi",
];

/** Google Translate target language code per res folder */
const FOLDER_TO_LANG = {
  "values-da": "da",
  "values-el": "el",
  "values-fa": "fa",
  "values-fi": "fi",
  "values-ga": "ga",
  "values-ha": "ha",
  "values-hr": "hr",
  "values-id": "id",
  "values-it": "it",
  "values-ja": "ja",
  "values-kk": "kk",
  "values-kn": "kn",
  "values-ko": "ko",
  "values-lo": "lo",
  "values-lt": "lt",
  "values-lv": "lv",
  "values-mk": "mk",
  "values-ml": "ml",
  "values-mr": "mr",
  "values-ms": "ms",
  "values-nb": "no",
  "values-nl": "nl",
  "values-om": "om",
  "values-pa": "pa",
  "values-pl": "pl",
  "values-pt-rBR": "pt",
  "values-pt-rPT": "pt",
  "values-ro": "ro",
  "values-ru": "ru",
  "values-sk": "sk",
  "values-sl": "sl",
  "values-sq": "sq",
  "values-sr": "sr",
  "values-sv": "sv",
  "values-sw": "sw",
  "values-ta": "ta",
  "values-te": "te",
  "values-th": "th",
  "values-tr": "tr",
  "values-uk": "uk",
  "values-ur": "ur",
  "values-uz": "uz",
  "values-vi": "vi",
};

/** Copy as-is (no MT) */
const SKIP_NAMES = new Set(["app_name", "keyboard_name"]);

function decodeAndroidInner(raw) {
  return raw.replace(/\\(.)/g, (_, c) => {
    if (c === "n") return "\n";
    if (c === "t") return "\t";
    if (c === "r") return "\r";
    if (c === "'") return "'";
    if (c === '"') return '"';
    if (c === "\\") return "\\";
    return "\\" + c;
  });
}

function encodeAndroidInner(text) {
  return text
    .replace(/\\/g, "\\\\")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "\\r")
    .replace(/\t/g, "\\t")
    .replace(/'/g, "\\'")
    .replace(/"/g, '\\"')
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function maskFormatSpecifiers(text) {
  const parts = [];
  const masked = text.replace(/%(?:\d+\$)?[sd%]|%%/g, (m) => {
    parts.push(m);
    return `ZZZPH${parts.length - 1}ZZZ`;
  });
  return { masked, parts };
}

function unmaskFormatSpecifiers(translated, parts) {
  let out = translated;
  for (let i = 0; i < parts.length; i++) {
    const re = new RegExp(`ZZZPH${i}ZZZ`, "g");
    out = out.replace(re, parts[i]);
  }
  return out;
}

function parseStringsXml(content) {
  const entries = [];
  const re = /<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(content)) !== null) {
    const name = m[1];
    const attrs = m[2];
    const innerRaw = m[3];
    entries.push({ name, attrs, innerRaw });
  }
  return entries;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function translateOne(text, targetLang) {
  if (!text.trim()) return text;
  const { masked, parts } = maskFormatSpecifiers(text);
  let attempt = 0;
  while (attempt < 5) {
    try {
      const res = await translate(masked, { from: "en", to: targetLang });
      const out = unmaskFormatSpecifiers(res.text || "", parts);
      return out;
    } catch (e) {
      attempt++;
      await sleep(500 * attempt);
      if (attempt >= 5) throw e;
    }
  }
  return text;
}

async function translateFolder(folderName, entries, templateHeader) {
  const lang = FOLDER_TO_LANG[folderName];
  if (!lang) throw new Error("No lang for " + folderName);

  const lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"];

  let idx = 0;
  for (const e of entries) {
    idx++;
    const decoded = decodeAndroidInner(e.innerRaw);
    let outText;
    if (SKIP_NAMES.has(e.name)) {
      outText = decoded;
    } else {
      process.stdout.write(`  [${folderName}] ${idx}/${entries.length} ${e.name}\r`);
      outText = await translateOne(decoded, lang);
      await sleep(80);
    }
    const encoded = encodeAndroidInner(outText);
    lines.push(`    <string name="${e.name}"${e.attrs}>${encoded}</string>`);
  }
  lines.push("</resources>");
  lines.push("");

  const outPath = path.join(REPO_ROOT, "app", "src", "main", "res", folderName, "strings.xml");
  fs.writeFileSync(outPath, lines.join("\n"), "utf8");
  console.log(`\n  Wrote ${outPath}`);
}

async function main() {
  const xml = fs.readFileSync(DEFAULT_STRINGS, "utf8");
  const entries = parseStringsXml(xml);
  console.log(`Parsed ${entries.length} strings from values/strings.xml`);

  const inventoryPath = path.join(__dirname, "string_inventory.json");
  fs.writeFileSync(
    inventoryPath,
    JSON.stringify(
      {
        source: "app/src/main/res/values/strings.xml",
        stringCount: entries.length,
        stringNames: entries.map((e) => e.name),
        targetLocales: TARGET_FOLDERS.map((f) => ({ folder: f, googleLang: FOLDER_TO_LANG[f] })),
      },
      null,
      2
    ),
    "utf8"
  );
  console.log(`Wrote inventory: ${inventoryPath}`);

  for (const folder of TARGET_FOLDERS) {
    console.log(`\nTranslating -> ${folder} (${FOLDER_TO_LANG[folder]})`);
    await translateFolder(folder, entries);
  }
  console.log("\nAll done.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
