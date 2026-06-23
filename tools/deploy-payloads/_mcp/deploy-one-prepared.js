#!/usr/bin/env node
/**
 * Deploy one edge function via prepared JSON args file.
 * Agent calls: node deploy-one-prepared.js <name>
 * Then CallMcpTool deploy_edge_function with args from _current-args.json
 */
const fs = require("fs");
const path = require("path");

const name = process.argv[2];
if (!name) {
  console.error("Usage: node deploy-one-prepared.js <function-name>");
  process.exit(1);
}

const src = path.join(__dirname, "_prepared", `${name}.json`);
const dst = path.join(__dirname, "_current-args.json");
const args = JSON.parse(fs.readFileSync(src, "utf8"));
const sec = args.files.find((f) => f.name.includes("security"));
if (!sec || sec.content.length !== 14287) {
  console.error(`Invalid security.ts: ${sec ? sec.content.length : "missing"} bytes`);
  process.exit(1);
}
fs.writeFileSync(dst, JSON.stringify(args), "utf8");
console.log(JSON.stringify({ name: args.name, verify_jwt: args.verify_jwt, ready: true, argsFile: dst }));
