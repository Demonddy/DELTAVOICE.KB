/**
 * Builds app/src/main/assets/predictive_hi.txt (5000 Devanagari words).
 * Requires tmp_hi.txt from:
 *   https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/hi/hi_full.txt
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const outPath = path.join(root, "app", "src", "main", "assets", "predictive_hi.txt");

/** Devanagari letters (excludes standalone danda etc.). */
function hasDevanagariLetter(s) {
  return /[\u0904-\u0939\u0958-\u0962]/.test(s);
}

function isHindiToken(w) {
  const t = w.trim();
  if (!t) return false;
  if (/^[\u0964\u0965\u0970]+$/.test(t)) return false;
  return hasDevanagariLetter(t);
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

const raw = loadFreqFile(path.join(root, "tmp_hi.txt"));
const seen = new Set();
const out = [];
for (const w of raw) {
  if (!isHindiToken(w)) continue;
  const t = w.trim();
  if (seen.has(t)) continue;
  seen.add(t);
  out.push(t);
  if (out.length >= 5000) break;
}

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, out.join("\n") + "\n", "utf8");
console.log("predictive_hi.txt", out.length, "->", outPath);
