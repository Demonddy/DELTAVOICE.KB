#!/usr/bin/env node
/** Print prepared deploy args JSON to stdout for MCP deploy_edge_function. */
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node load-prepared-args.js <function-name>');
  process.exit(1);
}
const file = path.join(__dirname, '_prepared', `${name}.json`);
const args = JSON.parse(fs.readFileSync(file, 'utf8'));
const sec = args.files.find((f) => f.name.includes('security'));
if (!sec || sec.content.length !== 14287) {
  console.error(`Invalid security.ts: ${sec ? sec.content.length : 'missing'} bytes`);
  process.exit(1);
}
process.stdout.write(JSON.stringify(args));
