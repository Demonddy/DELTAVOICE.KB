const fs = require('fs');
const content = fs.readFileSync('app/src/main/java/com/deltavoice/PredictiveWordList.kt', 'utf8');
const pattern = /private val (\w+): Map<String, Int> by lazy \{ wordsFromString\(([\s\S]*?)\)\}/g;
let match;
let totalWords = 0;
let langs = 0;
const under2000 = [];
const under500 = [];
while ((match = pattern.exec(content)) !== null) {
    const name = match[1];
    const body = match[2];
    const strings = [...body.matchAll(/"([^"]*)"/g)].map(m => m[1]);
    const words = strings.flatMap(s => s.split(/\s+/).filter(w => w.length > 0));
    const count = words.length;
    totalWords += count;
    langs++;
    console.log(`${name}: ${count} words`);
    if (['englishWords','spanishWords','frenchWords','germanWords','italianWords','portugueseWords','russianWords','arabicWords','hindiWords','japaneseWords','koreanWords','chinesePinyinWords'].includes(name)) {
        if (count < 2000) under2000.push(`${name}: ${count} (need ${2000 - count} more)`);
    } else {
        if (count < 500) under500.push(`${name}: ${count} (need ${500 - count} more)`);
    }
}
console.log(`\nTotal: ${langs} languages, ${totalWords} words`);
if (under2000.length) console.log(`\nOriginal 12 under 2000:\n${under2000.join('\n')}`);
if (under500.length) console.log(`\nAdditional under 500:\n${under500.join('\n')}`);
