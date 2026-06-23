#!/usr/bin/env node
/**
 * Load one function's MCP deploy args from *.args.json (stdout JSON).
 * Strips UTF-8 BOM from source files.
 */
const fs = require("fs");
const path = require("path");

const name = process.argv[2];
if (!name) {
  console.error("Usage: node mcp-deploy-runner.js <function-name>");
  process.exit(1);
}

const argsPath = path.join(__dirname, "deploy-payloads", "_mcp", `${name}.args.json`);
const args = JSON.parse(fs.readFileSync(argsPath, "utf8"));
for (const file of args.files) {
  if (file.content.charCodeAt(0) === 0xfeff) {
    file.content = file.content.slice(1);
  }
}
process.stdout.write(
  JSON.stringify({
    name: args.name,
    entrypoint_path: args.entrypoint_path,
    verify_jwt: args.verify_jwt,
    files: args.files,
  }),
);
