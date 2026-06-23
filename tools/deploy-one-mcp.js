#!/usr/bin/env node
/** Print deploy_edge_function MCP arguments for one function (stdout JSON). */
const fs = require("fs");
const path = require("path");

const name = process.argv[2];
if (!name) {
  console.error("Usage: node deploy-one-mcp.js <function-name>");
  process.exit(1);
}

const argsPath = path.join(__dirname, "deploy-payloads", "_mcp", `${name}.args.json`);
const args = JSON.parse(fs.readFileSync(argsPath, "utf8"));
process.stdout.write(
  JSON.stringify({
    name: args.name,
    entrypoint_path: args.entrypoint_path,
    verify_jwt: args.verify_jwt,
    files: args.files,
  }),
);
