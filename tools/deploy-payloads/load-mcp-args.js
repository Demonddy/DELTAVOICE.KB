#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const name = process.argv[2];
if (!name) {
  console.error("Usage: node load-mcp-args.js <function-name>");
  process.exit(1);
}
const file = path.join(__dirname, "_mcp", `${name}.args.json`);
process.stdout.write(fs.readFileSync(file, "utf8"));
