#!/usr/bin/env node
/** Print deploy args JSON to stdout for agent CallMcpTool. Usage: node run-callmcp-deploy.js <name> */
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node run-callmcp-deploy.js <function-name>');
  process.exit(1);
}
const file = path.join(__dirname, `${name}.args.json`);
if (!fs.existsSync(file)) {
  console.error(`Missing ${file}`);
  process.exit(1);
}
const args = JSON.parse(fs.readFileSync(file, 'utf8'));
const sec = args.files.find((f) => f.name.includes('security'));
if (!sec || sec.content.includes('PLACEHOLDER')) {
  console.error(`Invalid security.ts for ${name}`);
  process.exit(1);
}
process.stdout.write(JSON.stringify(args));
