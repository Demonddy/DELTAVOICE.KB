/**
 * Machine-translates values/strings.xml + values/arrays.xml into locale folders
 * whose strings.xml still matches the default English file (placeholders).
 *
 * Usage: node translate_locale_strings.js
 */
'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { XMLParser } = require('fast-xml-parser');
const gtx = require('google-translate-api-x');

const batchTranslate = gtx.batchTranslate;
const isSupported = gtx.isSupported;

const RES = path.join(__dirname, '..', 'app', 'src', 'main', 'res');
const DEFAULT_STRINGS = path.join(RES, 'values', 'strings.xml');
const DEFAULT_ARRAYS = path.join(RES, 'values', 'arrays.xml');

function protectPlaceholders(text) {
  const placeholders = [];
  const protectedText = text.replace(/%(?:\d+\$)?[sd]|%[sd]/g, (m) => {
    placeholders.push(m);
    return `__PH${placeholders.length - 1}__`;
  });
  return { protectedText, placeholders };
}

function restorePlaceholders(text, placeholders) {
  let out = text;
  for (let i = 0; i < placeholders.length; i++) {
    out = out.split(`__PH${i}__`).join(placeholders[i]);
  }
  return out;
}

function escapeAndroidXml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/'/g, "\\'")
    .replace(/\n/g, '\\n');
}

function normalizeStringList(raw) {
  if (!raw) return [];
  return Array.isArray(raw) ? raw : [raw];
}

function parseStringsXml(filePath) {
  const xml = fs.readFileSync(filePath, 'utf8');
  const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: '@_',
    isArray: (tagName) => tagName === 'string',
  });
  const doc = parser.parse(xml);
  const list = normalizeStringList(doc.resources?.string);
  return list.map((el) => ({
    name: el['@_name'],
    text: el['#text'] != null ? String(el['#text']) : '',
  })).filter((e) => e.name);
}

function parseArraysXml(filePath) {
  const xml = fs.readFileSync(filePath, 'utf8');
  const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: '@_',
    isArray: (tagName) => tagName === 'string-array' || tagName === 'item',
  });
  const doc = parser.parse(xml);
  const arrays = normalizeStringList(doc.resources?.['string-array']);
  return arrays.map((arr) => {
    const name = arr['@_name'];
    const items = normalizeStringList(arr.item).map((it) => String(it));
    return { name, items };
  });
}

function hashFile(p) {
  return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
}

const FOLDER_TO_LANG = {
  'values-da': 'da',
  'values-fa': 'fa',
  'values-fi': 'fi',
  'values-ga': 'ga',
  'values-ha': 'ha',
  'values-hr': 'hr',
  'values-id': 'id',
  'values-it': 'it',
  'values-ja': 'ja',
  'values-kk': 'kk',
  'values-kn': 'kn',
  'values-ko': 'ko',
  'values-lo': 'lo',
  'values-lt': 'lt',
  'values-lv': 'lv',
  'values-mk': 'mk',
  'values-ml': 'ml',
  'values-mr': 'mr',
  'values-ms': 'ms',
  'values-nb': 'no',
  'values-nl': 'nl',
  'values-om': 'om',
  'values-pa': 'pa',
  'values-pl': 'pl',
  'values-pt-rBR': 'pt',
  'values-pt-rPT': 'pt',
  'values-ro': 'ro',
  'values-ru': 'ru',
  'values-sk': 'sk',
  'values-sl': 'sl',
  'values-sq': 'sq',
  'values-sr': 'sr',
  'values-sv': 'sv',
  'values-sw': 'sw',
  'values-ta': 'ta',
  'values-te': 'te',
  'values-th': 'th',
  'values-tr': 'tr',
  'values-uk': 'uk',
  'values-ur': 'ur',
  'values-uz': 'uz',
  'values-vi': 'vi',
};

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function translateBatch(entries, targetLang, forceTo) {
  const pre = entries.map((e) => protectPlaceholders(e.sourceText));
  const inputs = pre.map((p) => ({
    text: p.protectedText,
    from: 'en',
    to: targetLang,
    ...(forceTo ? { forceTo: true } : {}),
  }));

  const results = await batchTranslate(inputs, {
    from: 'en',
    to: targetLang,
    ...(forceTo ? { forceTo: true } : {}),
    rejectOnPartialFail: false,
  });

  const out = [];
  for (let i = 0; i < entries.length; i++) {
    const r = Array.isArray(results) ? results[i] : results;
    const raw = r && r.text != null ? r.text : '';
    const fallback = raw || entries[i].sourceText;
    out.push(restorePlaceholders(fallback, pre[i].placeholders));
  }
  return out;
}

function buildStringsXml(entries) {
  const lines = [
    '<?xml version="1.0" encoding="utf-8"?>',
    '<resources>',
  ];
  for (const { name, text } of entries) {
    lines.push(`    <string name="${name}">${escapeAndroidXml(text)}</string>`);
  }
  lines.push('</resources>');
  lines.push('');
  return lines.join('\n');
}

function buildArraysXml(arrayDefs) {
  const lines = [
    '<?xml version="1.0" encoding="utf-8"?>',
    '<resources>',
  ];
  for (const arr of arrayDefs) {
    lines.push(`    <string-array name="${arr.name}">`);
    for (const item of arr.items) {
      lines.push(`        <item>${escapeAndroidXml(item)}</item>`);
    }
    lines.push('    </string-array>');
  }
  lines.push('</resources>');
  lines.push('');
  return lines.join('\n');
}

async function main() {
  const defaultHash = hashFile(DEFAULT_STRINGS);
  const dirs = fs.readdirSync(RES).filter((d) => d.startsWith('values-'));
  const placeholderFolders = [];

  for (const d of dirs) {
    const p = path.join(RES, d, 'strings.xml');
    if (!fs.existsSync(p)) continue;
    if (hashFile(p) === defaultHash) placeholderFolders.push(d);
  }

  console.log(`Placeholder locales (English copy): ${placeholderFolders.length}`);

  const baseStrings = parseStringsXml(DEFAULT_STRINGS);
  const baseArrays = parseArraysXml(DEFAULT_ARRAYS);

  for (const folder of placeholderFolders.sort()) {
    const targetLang = FOLDER_TO_LANG[folder];
    if (!targetLang) {
      console.warn(`No lang mapping for ${folder}, skip`);
      continue;
    }

    let forceTo = false;
    try {
      if (!isSupported(targetLang)) {
        console.warn(`Language ${targetLang} not in Google list, using forceTo for ${folder}`);
        forceTo = true;
      }
    } catch (_) {
      forceTo = true;
    }

    const destDir = path.join(RES, folder);
    const destStrings = path.join(destDir, 'strings.xml');
    const destArrays = path.join(destDir, 'arrays.xml');

    console.log(`Translating ${folder} -> ${targetLang} ...`);

    try {
      const stringEntries = baseStrings.map((s) => ({
        name: s.name,
        sourceText: s.text,
      }));

      const translatedTexts = await translateBatch(stringEntries, targetLang, forceTo);
      const stringOut = stringEntries.map((e, i) => {
        let text = translatedTexts[i];
        if (e.name === 'app_name' || e.name === 'keyboard_name') {
          text = 'deltavoice';
        }
        return { name: e.name, text };
      });

      fs.writeFileSync(destStrings, buildStringsXml(stringOut), 'utf8');

      const arrayJobs = [];
      for (const arr of baseArrays) {
        const itemEntries = arr.items.map((item) => ({
          name: '',
          sourceText: item,
        }));
        const tr = await translateBatch(itemEntries, targetLang, forceTo);
        arrayJobs.push({ name: arr.name, items: tr });
      }
      fs.writeFileSync(destArrays, buildArraysXml(arrayJobs), 'utf8');
    } catch (err) {
      console.error(`FAILED ${folder}:`, err.message);
      continue;
    }

    await sleep(600);
  }

  console.log('Done.');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
