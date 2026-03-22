const fs = require('fs');
const path = require('path');
const kb = path.join(__dirname, '../app/src/main/res/layout/keyboard_layout.xml');
const lines = fs.readFileSync(kb, 'utf8').split(/\r?\n/);

function slice(name, start, end) {
  const out = path.join(__dirname, '../app/src/main/res/layout', name);
  const body = lines.slice(start, end).join('\n');
  fs.writeFileSync(out, '<?xml version="1.0" encoding="utf-8"?>\n' + body);
}

slice('overlay_voice_step2.xml', 363, 686);
slice('overlay_video_preview.xml', 797, 979);
console.log('ok');
