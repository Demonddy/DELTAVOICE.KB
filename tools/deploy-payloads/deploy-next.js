#!/usr/bin/env node
/** Deploy one edge function by reading prepared JSON and printing result path for MCP agent loop. */
const fs = require('fs');
const path = require('path');
const ORDER = process.argv[2] === 'all'
  ? ['password-reset','stripe-webhook','admin-dashboard','create-checkout','customer-portal','check-subscription','ai-chat','translate-text','writing-tool','complete-voice-workflow','voice-to-text','voice-conversion','create-voice-clone','free-translate-text','free-voice-translate','get-deepgram-key']
  : [process.argv[2]];
const base = __dirname;
const resultsPath = path.join(base, '_deploy-results.json');
let results = fs.existsSync(resultsPath) ? JSON.parse(fs.readFileSync(resultsPath, 'utf8')) : [];
const done = new Set(results.map((r) => r.name));
for (const name of ORDER) {
  if (done.has(name)) continue;
  const src = path.join(base, '_mcp', '_prepared', `${name}.json`);
  const payload = JSON.parse(fs.readFileSync(src, 'utf8'));
  fs.writeFileSync(path.join(base, '_deploy-current.json'), JSON.stringify(payload));
  console.log('NEXT', name);
  process.exit(0);
}
console.log('ALL_DONE', results.length);
