/**
 * Translates locale strings that still match the English default (partially translated files).
 * Usage: node translate_stale_locale_strings.js [--dry-run] [--locale values-ar]
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { XMLParser } = require('fast-xml-parser');
const gtx = require('google-translate-api-x');

const batchTranslate = gtx.batchTranslate;
const isSupported = gtx.isSupported;

const RES = path.join(__dirname, '..', 'app', 'src', 'main', 'res');
const DEFAULT_STRINGS = path.join(RES, 'values', 'strings.xml');
const DEFAULT_ARRAYS = path.join(RES, 'values', 'arrays.xml');

const FOLDER_TO_LANG = {
  'values-af': 'af', 'values-am': 'am', 'values-ar': 'ar', 'values-az': 'az',
  'values-bg': 'bg', 'values-bn': 'bn', 'values-ca': 'ca', 'values-cs': 'cs',
  'values-da': 'da', 'values-de': 'de', 'values-el': 'el', 'values-es': 'es',
  'values-et': 'et', 'values-fa': 'fa', 'values-fi': 'fi', 'values-fil': 'fil',
  'values-fr': 'fr', 'values-ga': 'ga', 'values-gu': 'gu', 'values-ha': 'ha',
  'values-he': 'he', 'values-hi': 'hi', 'values-hr': 'hr', 'values-hu': 'hu',
  'values-id': 'id', 'values-iw': 'he', 'values-ja': 'ja', 'values-kk': 'kk',
  'values-kn': 'kn', 'values-ko': 'ko', 'values-lo': 'lo', 'values-lt': 'lt',
  'values-lv': 'lv', 'values-mk': 'mk', 'values-ml': 'ml', 'values-mr': 'mr',
  'values-ms': 'ms', 'values-nb': 'no', 'values-nl': 'nl', 'values-om': 'om',
  'values-pa': 'pa', 'values-pl': 'pl', 'values-pt-rBR': 'pt', 'values-pt-rPT': 'pt',
  'values-ro': 'ro', 'values-ru': 'ru', 'values-sk': 'sk', 'values-sl': 'sl',
  'values-sq': 'sq', 'values-sr': 'sr', 'values-sv': 'sv', 'values-sw': 'sw',
  'values-ta': 'ta', 'values-te': 'te', 'values-th': 'th', 'values-tr': 'tr',
  'values-uk': 'uk', 'values-ur': 'ur', 'values-uz': 'uz', 'values-vi': 'vi',
  'values-zh-rCN': 'zh-CN', 'values-zh-rHK': 'zh-TW', 'values-zh-rTW': 'zh-TW',
};

const SKIP_STRINGS = new Set(['app_name', 'keyboard_name']);

const TRANSLATABLE_ARRAYS = new Set(['default_language_options', 'default_voice_options']);

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

function unescapeAndroidXml(text) {
  return text
    .replace(/\\n/g, '\n')
    .replace(/\\'/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

function parseStringsMap(filePath) {
  const xml = fs.readFileSync(filePath, 'utf8');
  const map = new Map();
  const re = /<string\s+name="([^"]+)"(?:\s+[^>]*)?>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(xml))) {
    map.set(m[1], unescapeAndroidXml(m[2]));
  }
  return { xml, map };
}

function parseArraysMap(filePath) {
  const xml = fs.readFileSync(filePath, 'utf8');
  const map = new Map();
  const arrayRe = /<string-array\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string-array>/g;
  let m;
  while ((m = arrayRe.exec(xml))) {
    const items = [];
    const itemRe = /<item>([\s\S]*?)<\/item>/g;
    let im;
    while ((im = itemRe.exec(m[2]))) {
      items.push(unescapeAndroidXml(im[1]));
    }
    map.set(m[1], items);
  }
  return { xml, map };
}

function replaceStringInXml(xml, name, newText) {
  const escaped = escapeAndroidXml(newText);
  const re = new RegExp(
    `(<string\\s+name="${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"(?:\\s+[^>]*)?>)([\\s\\S]*?)(</string>)`,
    'g'
  );
  return xml.replace(re, `$1${escaped}$3`);
}

function replaceArrayInXml(xml, name, items) {
  const block = [
    `    <string-array name="${name}">`,
    ...items.map((it) => `        <item>${escapeAndroidXml(it)}</item>`),
    '    </string-array>',
  ].join('\n');
  const re = new RegExp(
    `[ \\t]*<string-array\\s+name="${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"[^>]*>[\\s\\S]*?</string-array>`,
    'g'
  );
  return xml.replace(re, block);
}

async function translateTexts(texts, targetLang) {
  if (texts.length === 0) return [];
  const pre = texts.map((t) => protectPlaceholders(t));
  let forceTo = false;
  try {
    if (!isSupported(targetLang)) forceTo = true;
  } catch (_) {
    forceTo = true;
  }
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
  return texts.map((orig, i) => {
    const r = Array.isArray(results) ? results[i] : results;
    const raw = r && r.text != null ? r.text : '';
    return restorePlaceholders(raw || orig, pre[i].placeholders);
  });
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const dryRun = process.argv.includes('--dry-run');
  const onlyLocale = process.argv.find((a) => a.startsWith('--locale='))?.split('=')[1]
    || (process.argv.includes('--locale') ? process.argv[process.argv.indexOf('--locale') + 1] : null);

  const { map: defaultStrings } = parseStringsMap(DEFAULT_STRINGS);
  const defaultArrays = fs.existsSync(DEFAULT_ARRAYS) ? parseArraysMap(DEFAULT_ARRAYS).map : new Map();

  const dirs = fs.readdirSync(RES)
    .filter((d) => d.startsWith('values-'))
    .filter((d) => !onlyLocale || d === onlyLocale)
    .sort();

  for (const folder of dirs) {
    const targetLang = FOLDER_TO_LANG[folder];
    if (!targetLang) {
      console.warn(`Skip ${folder}: no lang mapping`);
      continue;
    }

    const stringsPath = path.join(RES, folder, 'strings.xml');
    if (!fs.existsSync(stringsPath)) continue;

    const { xml: stringsXml, map: localeStrings } = parseStringsMap(stringsPath);
    const stale = [];
    for (const [name, enText] of defaultStrings) {
      if (SKIP_STRINGS.has(name)) continue;
      const locText = localeStrings.get(name);
      if (locText != null && locText === enText && enText.trim()) {
        stale.push({ name, sourceText: enText });
      }
    }

    let updatedStringsXml = stringsXml;
    if (stale.length > 0) {
      console.log(`${folder}: translating ${stale.length} stale strings...`);
      if (!dryRun) {
        const BATCH = 40;
        for (let i = 0; i < stale.length; i += BATCH) {
          const chunk = stale.slice(i, i + BATCH);
          const translated = await translateTexts(chunk.map((e) => e.sourceText), targetLang);
          for (let j = 0; j < chunk.length; j++) {
            let text = translated[j];
            if (chunk[j].name === 'app_name' || chunk[j].name === 'keyboard_name') {
              text = 'deltavoice';
            }
            updatedStringsXml = replaceStringInXml(updatedStringsXml, chunk[j].name, text);
          }
          await sleep(300);
        }
        fs.writeFileSync(stringsPath, updatedStringsXml, 'utf8');
      }
    } else {
      console.log(`${folder}: strings up to date`);
    }

    const arraysPath = path.join(RES, folder, 'arrays.xml');
    if (!fs.existsSync(arraysPath)) continue;

    const { xml: arraysXml, map: localeArrays } = parseArraysMap(arraysPath);
    let updatedArraysXml = arraysXml;
    let arrayChanges = 0;

    for (const arrayName of TRANSLATABLE_ARRAYS) {
      const enItems = defaultArrays.get(arrayName);
      const locItems = localeArrays.get(arrayName);
      if (!enItems || !locItems || enItems.length !== locItems.length) continue;

      const staleIdx = [];
      for (let i = 0; i < enItems.length; i++) {
        if (locItems[i] === enItems[i] && enItems[i].trim()) staleIdx.push(i);
      }
      if (staleIdx.length === 0) continue;

      console.log(`${folder}: translating ${staleIdx.length} stale items in ${arrayName}...`);
      if (!dryRun) {
        const toTranslate = staleIdx.map((i) => enItems[i]);
        const translated = await translateTexts(toTranslate, targetLang);
        const newItems = [...locItems];
        staleIdx.forEach((idx, j) => {
          newItems[idx] = translated[j];
        });
        updatedArraysXml = replaceArrayInXml(updatedArraysXml, arrayName, newItems);
        arrayChanges++;
        await sleep(200);
      }
    }

    if (arrayChanges > 0 && !dryRun) {
      fs.writeFileSync(arraysPath, updatedArraysXml, 'utf8');
    }
  }

  console.log(dryRun ? 'Dry run complete.' : 'Done.');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
