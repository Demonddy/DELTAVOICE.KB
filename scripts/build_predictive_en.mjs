/**
 * Builds app/src/main/assets/predictive_en.txt (~50k unique English words).
 * Source: tmp_en_words.txt — HermitDave frequency format (word count), e.g.:
 *   https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt
 * Plain one-word-per-line files also work (no trailing count).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const src = path.join(root, "tmp_en_words.txt");
const outPath = path.join(root, "app", "src", "main", "assets", "predictive_en.txt");
const TARGET_COUNT = 50_000;

function loadFreqFile(p) {
  const raw = fs.readFileSync(p, "utf8");
  const words = [];
  for (const line of raw.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const space = line.lastIndexOf(" ");
    const word = space === -1 ? line.trim() : line.slice(0, space).trim();
    if (word) words.push(word);
  }
  return words;
}

function isAsciiEnglishWord(w) {
  const t = w.trim().toLowerCase();
  if (!t) return false;
  return [...t].every((c) => /[a-z']/.test(c));
}

const tokens = loadFreqFile(src);
const seen = new Set();
const words = [];
for (const token of tokens) {
  const w = token.trim().toLowerCase();
  if (!w || seen.has(w)) continue;
  if (!isAsciiEnglishWord(w)) continue;
  seen.add(w);
  words.push(w);
  if (words.length >= TARGET_COUNT) break;
}

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, words.join("\n") + "\n", "utf8");
console.log(`written ${words.length} unique words to ${outPath}`);
