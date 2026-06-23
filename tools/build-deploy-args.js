#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const name = process.argv[2];
const outPath = process.argv[3];
if (!name) {
  console.error('Usage: node build-deploy-args.js <function-name> [out-path]');
  process.exit(1);
}

const base = path.join(__dirname, 'deploy-payloads', '_mcp');
const args = JSON.parse(fs.readFileSync(path.join(base, `${name}.args.json`), 'utf8'));
const payload = JSON.stringify({
  name: args.name,
  entrypoint_path: args.entrypoint_path,
  verify_jwt: args.verify_jwt,
  files: args.files,
});

if (outPath) {
  fs.writeFileSync(outPath, payload, 'utf8');
  console.log(`WROTE ${outPath} (${payload.length} bytes)`);
} else {
  process.stdout.write(payload);
}
