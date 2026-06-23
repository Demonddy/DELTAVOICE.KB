#!/usr/bin/env node
/**
 * Deploy edge functions via CallMcpTool-compatible args files.
 * Reads {name}.args.json and invokes deploy_edge_function through
 * the Cursor user-supabase MCP by writing invoke payloads.
 *
 * Agent workflow: node mcp-deploy-one.js <name> then CallMcpTool with _invoke.json
 */
const fs = require('fs');
const path = require('path');

const name = process.argv[2];
if (!name) {
  console.error('Usage: node mcp-deploy-one.js <function-name>');
  process.exit(1);
}

const src = path.join(__dirname, `${name}.args.json`);
if (!fs.existsSync(src)) {
  console.error(`Missing ${src}`);
  process.exit(1);
}

const args = JSON.parse(fs.readFileSync(src, 'utf8'));
const invokePath = path.join(__dirname, '_invoke.json');
fs.writeFileSync(invokePath, JSON.stringify(args), 'utf8');
console.log(JSON.stringify({
  ready: true,
  name: args.name,
  verify_jwt: args.verify_jwt,
  files: args.files.length,
  invokePath,
  bytes: fs.statSync(invokePath).size,
}));
