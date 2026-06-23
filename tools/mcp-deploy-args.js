#!/usr/bin/env node
/**
 * Reads deploy args for one function and prints MCP-ready JSON to stdout.
 */
const fs = require('fs');
const path = require('path');

const name = process.argv[2];
if (!name) {
  console.error('Usage: node mcp-deploy-args.js <function-name>');
  process.exit(1);
}

const argsPath = path.join(__dirname, 'deploy-payloads', '_mcp', `${name}.args.json`);
const args = JSON.parse(fs.readFileSync(argsPath, 'utf8'));
const outPath = process.argv[3];
const payload = JSON.stringify(args);
if (outPath) {
  fs.writeFileSync(outPath, payload, 'utf8');
} else {
  process.stdout.write(payload);
}
