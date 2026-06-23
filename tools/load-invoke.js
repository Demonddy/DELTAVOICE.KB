#!/usr/bin/env node
/** Load MCP deploy args for one function. Usage: node tools/load-invoke.js <name> */
const fs = require("fs");
const path = require("path");
const name = process.argv[2];
if (!name) process.exit(1);
const p = path.join(__dirname, "deploy-payloads", "_mcp", `_invoke-${name}.json`);
module.exports = JSON.parse(fs.readFileSync(p, "utf8"));
