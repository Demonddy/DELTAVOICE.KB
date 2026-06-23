#!/usr/bin/env node
/**
 * Load deploy args for a function and print summary or full JSON path.
 */
const fs = require('fs');
const path = require('path');

const name = process.argv[2];
const mode = process.argv[3] || 'summary';
const base = path.join(__dirname, 'deploy-payloads', '_mcp');
const argsPath = path.join(base, `${name}.args.json`);
const args = JSON.parse(fs.readFileSync(argsPath, 'utf8'));

if (mode === 'full') {
  process.stdout.write(JSON.stringify(args));
} else {
  console.log(JSON.stringify({
    name: args.name,
    entrypoint_path: args.entrypoint_path,
    verify_jwt: args.verify_jwt,
    files: args.files.map((f) => ({ name: f.name, bytes: f.content.length })),
  }));
}
