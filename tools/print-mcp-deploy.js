#!/usr/bin/env node
/**
 * Print MCP deploy args for one function (for agent CallMcpTool).
 * Usage: node tools/print-mcp-deploy.js <function-name>
 */
const fs = require("fs");
const path = require("path");

const name = process.argv[2];
if (!name) {
  console.error("Usage: node tools/print-mcp-deploy.js <function-name>");
  process.exit(1);
}

const p = path.join(__dirname, "deploy-payloads", "_mcp", `_invoke-${name}.json`);
if (!fs.existsSync(p)) {
  console.error(`Missing ${p}`);
  process.exit(1);
}
process.stdout.write(fs.readFileSync(p, "utf8"));
