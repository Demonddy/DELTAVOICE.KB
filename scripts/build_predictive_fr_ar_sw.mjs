/**
 * Builds predictive_fr.txt, predictive_ar.txt, predictive_sw.txt (5000 unique lines each).
 * Sources:
 *   tmp_fr.txt, tmp_ar.txt — HermitDave frequency format ("word count")
 *   tmp_sw_dic.txt — LibreOffice sw_TZ.dic (hunspell)
 *   Swahili: merged tokens from worddata/sw.txt + word_data [sw], then hunspell to fill.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const outDir = path.join(root, "app", "src", "main", "assets");

function hasArabicLetter(s) {
  return /[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]/.test(s);
}

/** Reject punctuation-only tokens (e.g. Arabic comma U+060C). */
function isArabicWord(w) {
  const t = w.trim();
  if (!t) return false;
  if (/^[\u0600-\u060F\u061B\u061F\u0640]+$/.test(t)) return false;
  return hasArabicLetter(t);
}

function write5000(name, words) {
  const outPath = path.join(outDir, name);
  const seen = new Set();
  const out = [];
  for (const w of words) {
    const t = typeof w === "string" ? w.trim() : "";
    if (!t || seen.has(t)) continue;
    seen.add(t);
    out.push(t);
    if (out.length >= 5000) break;
  }
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(outPath, out.join("\n") + "\n", "utf8");
  console.log(name, out.length, "->", outPath);
}

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

function loadHunspellDic(p) {
  const raw = fs.readFileSync(p, "utf8");
  const lines = raw.split(/\r?\n/);
  const words = [];
  for (let i = 1; i < lines.length; i++) {
    let line = lines[i].trim();
    if (!line) continue;
    const slash = line.indexOf("/");
    if (slash !== -1) line = line.slice(0, slash);
    const w = line.trim();
    if (w && !/^\d+$/.test(w)) words.push(w);
  }
  return words;
}

function loadSwahiliSeed(rootDir) {
  const wd = fs.readFileSync(path.join(rootDir, "worddata", "sw.txt"), "utf8");
  const wdTok = wd.split(/\s+/).filter(Boolean);
  const data = fs.readFileSync(path.join(rootDir, "word_data.txt"), "utf8").split(/\r?\n/);
  let i = data.findIndex((l) => l.trim() === "[sw]");
  const extra = [];
  for (i++; i < data.length && !data[i].startsWith("["); i++) {
    data[i].split(/\s+/).forEach((w) => w && extra.push(w));
  }
  const seen = new Set();
  const out = [];
  for (const w of [...wdTok, ...extra]) {
    const t = w.trim();
    if (!t || seen.has(t)) continue;
    seen.add(t);
    out.push(t);
  }
  return out;
}

function isSwahiliToken(w) {
  const t = w.trim();
  if (t.length < 2) return false;
  return /^[a-zA-Z'-]+$/.test(t);
}

// French
const frWords = loadFreqFile(path.join(root, "tmp_fr.txt"));
write5000("predictive_fr.txt", frWords);

// Arabic: drop punctuation-only lines
const arRaw = loadFreqFile(path.join(root, "tmp_ar.txt"));
write5000("predictive_ar.txt", arRaw.filter(isArabicWord));

// Swahili: curated first (frequency-ish), then hunspell Latin tokens
const swSeed = loadSwahiliSeed(root);
const swHun = loadHunspellDic(path.join(root, "tmp_sw_dic.txt"));
const swCombined = [
  ...swSeed,
  ...swHun.filter(isSwahiliToken),
];
write5000("predictive_sw.txt", swCombined);
