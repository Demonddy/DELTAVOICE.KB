#!/usr/bin/env node
/** Print deploy_edge_function arguments JSON for one function (stdout). */
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node print-args.js <function-name>');
  process.exit(1);
}
const file = path.join(__dirname, `${name}.args.json`);
process.stdout.write(fs.readFileSync(file, 'utf8'));
