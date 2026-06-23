#!/usr/bin/env node
/** Load deploy args JSON for CallMcpTool. Usage: node load-args-for-mcp.js <name> */
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node load-args-for-mcp.js <function-name>');
  process.exit(1);
}
const candidates = [
  path.join(__dirname, `${name}.args.json`),
  path.join(__dirname, `_deploy-${name}.json`),
];
const file = candidates.find((f) => fs.existsSync(f));
if (!file) {
  console.error(`No args file for ${name}`);
  process.exit(1);
}
const args = JSON.parse(fs.readFileSync(file, 'utf8'));
const sec = args.files.find((f) => f.name.includes('security'));
if (!sec || sec.content.includes('PLACEHOLDER')) {
  console.error(`Invalid security.ts for ${name}`);
  process.exit(1);
}
process.stdout.write(JSON.stringify(args));
