/**
 * Builds predictive_es.txt, predictive_pt.txt (~10k unique lines each).
 * Requires tmp_es.txt, tmp_pt.txt (HermitDave frequency format "word count") from:
 *   https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_full.txt
 *   https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/pt/pt_full.txt
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const outDir = path.join(root, "app", "src", "main", "assets");
const TARGET_COUNT = 10_000;

/** Latin / extended Latin tokens only (Spanish & Portuguese). */
function isLatinExtendedWord(w) {
  const t = w.trim();
  if (!t) return false;
  return /^[\p{L}\p{M}'-]+$/u.test(t);
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

function writePredictive(name, words) {
  const outPath = path.join(outDir, name);
  const seen = new Set();
  const out = [];
  for (const w of words) {
    if (!isLatinExtendedWord(w)) continue;
    const t = w.trim();
    if (seen.has(t)) continue;
    seen.add(t);
    out.push(t);
    if (out.length >= TARGET_COUNT) break;
  }
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(outPath, out.join("\n") + "\n", "utf8");
  console.log(name, out.length, "->", outPath);
}

const esWords = loadFreqFile(path.join(root, "tmp_es.txt"));
writePredictive("predictive_es.txt", esWords);

const ptWords = loadFreqFile(path.join(root, "tmp_pt.txt"));
writePredictive("predictive_pt.txt", ptWords);
