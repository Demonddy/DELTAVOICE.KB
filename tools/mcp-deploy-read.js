#!/usr/bin/env node
/** Deploy one function via user-supabase MCP JSON args file. Prints MCP-ready JSON to stdout. */
const fs = require("fs");
const path = require("path");
const name = process.argv[2];
if (!name) {
  console.error("Usage: node mcp-deploy-read.js <function-name>");
  process.exit(1);
}
const p = path.join(__dirname, "deploy-payloads", "_mcp", `deploy-${name}.json`);
const payload = JSON.parse(fs.readFileSync(p, "utf8"));
// Strip UTF-8 BOM from index.ts if present
for (const file of payload.files) {
  if (file.content.charCodeAt(0) === 0xfeff) {
    file.content = file.content.slice(1);
  }
}
process.stdout.write(
  JSON.stringify({
    name: payload.name,
    entrypoint_path: payload.entrypoint_path,
    verify_jwt: payload.verify_jwt,
    files: payload.files,
  }),
);
