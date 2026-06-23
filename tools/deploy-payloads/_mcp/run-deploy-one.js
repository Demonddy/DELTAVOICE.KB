#!/usr/bin/env node
/** Load prepared deploy args for MCP deploy_edge_function. Usage: node run-deploy-one.js <name> */
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node run-deploy-one.js <function-name>');
  process.exit(1);
}
const file = path.join(__dirname, '_call-' + name + '.json');
if (!fs.existsSync(file)) {
  const prep = path.join(__dirname, '_deploy-queue', name + '.json');
  if (!fs.existsSync(prep)) {
    console.error('Missing prepared args for', name);
    process.exit(1);
  }
  fs.writeFileSync(file, fs.readFileSync(prep, 'utf8'));
}
const args = JSON.parse(fs.readFileSync(file, 'utf8'));
const sec = args.files.find((f) => f.name.includes('security'));
if (!sec || sec.content.length !== 14287) {
  console.error('security.ts must be 14287 bytes, got', sec ? sec.content.length : 0);
  process.exit(1);
}
process.stdout.write(JSON.stringify(args));
