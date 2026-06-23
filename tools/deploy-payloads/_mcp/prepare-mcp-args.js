#!/usr/bin/env node
/** Write arguments-only JSON for CallMcpTool deploy_edge_function. */
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node prepare-mcp-args.js <function-name>');
  process.exit(1);
}
const src = path.join(__dirname, `${name}.args.json`);
const dst = path.join(__dirname, '_args-only.json');
const args = JSON.parse(fs.readFileSync(src, 'utf8'));
fs.writeFileSync(dst, JSON.stringify(args), 'utf8');
console.log(JSON.stringify({ name: args.name, verify_jwt: args.verify_jwt, bytes: fs.statSync(dst).size }));
