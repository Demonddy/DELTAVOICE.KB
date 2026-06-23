#!/usr/bin/env node
/** Emit deploy args JSON to stdout. Usage: node emit-args.js <name> */
const fs = require('fs');
const path = require('path');
const name = process.argv[2];
const file = path.join(__dirname, `${name}.args.json`);
process.stdout.write(fs.readFileSync(file, 'utf8'));
