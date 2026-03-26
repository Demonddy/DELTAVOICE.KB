import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const src = path.join(root, "tmp_en_words.txt");
const outPath = path.join(root, "app", "src", "main", "assets", "predictive_en.txt");
/** Target unique words (Google 10k list has 10k lines; dedupe may yield slightly fewer). */
const TARGET_COUNT = 10_000;

const raw = fs.readFileSync(src, "utf8");
const seen = new Set();
const words = [];
for (const line of raw.split(/\r?\n/)) {
  const w = line.trim().toLowerCase();
  if (!w || seen.has(w)) continue;
  if (![...w].every((c) => /[a-z']/.test(c))) continue;
  seen.add(w);
  words.push(w);
  if (words.length >= TARGET_COUNT) break;
}
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, words.join("\n") + "\n", "utf8");
console.log(`written ${words.length} unique words to ${outPath}`);
