#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const name = process.argv[2];
const field = process.argv[3] || 'index';
const base = path.join(__dirname, 'deploy-payloads', '_mcp');
const args = JSON.parse(fs.readFileSync(path.join(base, `${name}.args.json`), 'utf8'));

if (field === 'meta') {
  console.log(JSON.stringify({ name: args.name, verify_jwt: args.verify_jwt, entrypoint_path: args.entrypoint_path }));
} else if (field === 'index') {
  process.stdout.write(args.files.find((f) => f.name === 'index.ts').content);
} else if (field === 'security') {
  process.stdout.write(args.files.find((f) => f.name.includes('security')).content);
} else if (field === 'full') {
  process.stdout.write(JSON.stringify({
    name: args.name,
    entrypoint_path: args.entrypoint_path,
    verify_jwt: args.verify_jwt,
    files: args.files,
  }));
}
