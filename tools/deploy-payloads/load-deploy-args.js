#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node load-deploy-args.js <function-name> [out-file]');
  process.exit(1);
}
const payload = JSON.parse(
  fs.readFileSync(path.join(__dirname, `${name}.json`), 'utf8'),
);
const args = {
  name: payload.name,
  entrypoint_path: payload.entrypoint_path,
  verify_jwt: payload.verify_jwt,
  files: payload.files,
};
const out = process.argv[3] || path.join(__dirname, '_deploy-current.json');
fs.writeFileSync(out, JSON.stringify(args), 'utf8');
console.log(name);
