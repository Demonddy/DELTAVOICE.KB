#!/usr/bin/env node
/**
 * Reads deploy-payloads/*.json and prints one-line JSON args for MCP deploy_edge_function.
 * Usage: node mcp-batch-invoke.js <function-name>
 */
const fs = require("fs");
const path = require("path");

const dir = __dirname;
const name = process.argv[2];
if (!name) {
  console.error("Usage: node mcp-batch-invoke.js <function-name>");
  process.exit(1);
}

const payload = JSON.parse(
  fs.readFileSync(path.join(dir, `${name}.json`), "utf8"),
);
const args = {
  name: payload.name,
  entrypoint_path: payload.entrypoint_path,
  verify_jwt: payload.verify_jwt,
  files: payload.files,
};
process.stdout.write(JSON.stringify(args));
