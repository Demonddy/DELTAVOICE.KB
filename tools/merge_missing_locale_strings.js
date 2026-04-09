/**
 * Appends string entries from values/strings.xml missing in each locale strings.xml.
 * Usage: node merge_missing_locale_strings.js
 */
'use strict';

const fs = require('fs');
const path = require('path');

const RES = path.join(__dirname, '..', 'app', 'src', 'main', 'res');
const DEFAULT = path.join(RES, 'values', 'strings.xml');

function parseStrings(xml) {
  const map = new Map();
  const re = /<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(xml))) {
    map.set(m[1], m[0].trim());
  }
  return map;
}

function insertBeforeClosingResources(content, blocks) {
  const idx = content.lastIndexOf('</resources>');
  if (idx === -1) return content + '\n' + blocks + '\n</resources>\n';
  const insert = '\n    <!-- Merged missing keys from default -->\n' + blocks.map((b) => '    ' + b).join('\n') + '\n';
  return content.slice(0, idx) + insert + content.slice(idx);
}

function main() {
  const defaultXml = fs.readFileSync(DEFAULT, 'utf8');
  const defaultMap = parseStrings(defaultXml);

  const dirs = fs.readdirSync(RES).filter((d) => d.startsWith('values-'));
  for (const d of dirs.sort()) {
    const p = path.join(RES, d, 'strings.xml');
    if (!fs.existsSync(p)) continue;
    let localeXml = fs.readFileSync(p, 'utf8');
    const localeMap = parseStrings(localeXml);
    const missing = [];
    for (const [name, block] of defaultMap) {
      if (!localeMap.has(name)) {
        missing.push(block);
      }
    }
    if (missing.length === 0) continue;
    console.log(`${d}: adding ${missing.length} missing strings`);
    const updated = insertBeforeClosingResources(localeXml, missing);
    fs.writeFileSync(p, updated, 'utf8');
  }
  console.log('Done.');
}

main();
