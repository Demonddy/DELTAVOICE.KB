#!/usr/bin/env node
/**
 * Extract deploy args for MCP deploy_edge_function from prebuilt .args.json files.
 * Usage: node get-mcp-deploy-args.js <function-name>
 * Prints JSON to stdout (UTF-8, no BOM).
 */
const fs = require('fs');
const path = require('path');

const name = process.argv[2];
if (!name) {
  console.error('Usage: node get-mcp-deploy-args.js <function-name>');
  process.exit(1);
}

const argsPath = path.join(__dirname, 'deploy-payloads', '_mcp', `${name}.args.json`);
if (!fs.existsSync(argsPath)) {
  console.error(`Missing args file: ${argsPath}`);
  process.exit(1);
}

const args = JSON.parse(fs.readFileSync(argsPath, 'utf8'));
const payload = {
  name: args.name,
  entrypoint_path: args.entrypoint_path,
  verify_jwt: args.verify_jwt,
  files: args.files,
};
const outPath = process.argv[3];
const json = JSON.stringify(payload);
if (outPath) {
  fs.writeFileSync(outPath, json, 'utf8');
  console.log(`WROTE ${outPath} (${json.length} bytes)`);
} else {
  process.stdout.write(json);
}
