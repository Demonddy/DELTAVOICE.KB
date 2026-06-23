#!/usr/bin/env node
/**
 * Deploy edge functions sequentially by shelling out to cursor agent MCP bridge.
 * Primary use: agent reads stdout JSON and calls CallMcpTool deploy_edge_function.
 * 
 * Usage: node deploy-loop.js [function-name|all]
 */
const fs = require("fs");
const path = require("path");

const dir = __dirname;
const mcpDir = path.join(dir, "_mcp");
const logFile = path.join(dir, "_deploy-results.json");

const order = [
  "password-reset",
  "stripe-webhook",
  "admin-dashboard",
  "create-checkout",
  "customer-portal",
  "check-subscription",
  "ai-chat",
  "translate-text",
  "writing-tool",
  "complete-voice-workflow",
  "voice-to-text",
  "voice-conversion",
  "create-voice-clone",
  "free-translate-text",
  "free-voice-translate",
  "get-deepgram-key",
];

function loadArgs(name) {
  return JSON.parse(
    fs.readFileSync(path.join(mcpDir, `${name}.args.json`), "utf8"),
  );
}

const target = process.argv[2] || "all";
const names = target === "all" ? order : [target];

if (process.argv.includes("--print")) {
  for (const name of names) {
    process.stdout.write(`=== ${name} ===\n`);
    process.stdout.write(JSON.stringify(loadArgs(name)) + "\n");
  }
  process.exit(0);
}

if (process.argv.includes("--next")) {
  let results = [];
  if (fs.existsSync(logFile)) {
    results = JSON.parse(fs.readFileSync(logFile, "utf8"));
  }
  const done = new Set(results.map((r) => r.name));
  const next = order.find((n) => !done.has(n));
  if (!next) {
    console.log("ALL_DONE");
    process.exit(0);
  }
  console.log(next);
  process.exit(0);
}

if (process.argv.includes("--record")) {
  const name = process.argv[process.argv.indexOf("--record") + 1];
  const resultJson = process.argv[process.argv.indexOf("--record") + 2];
  let results = [];
  if (fs.existsSync(logFile)) {
    results = JSON.parse(fs.readFileSync(logFile, "utf8"));
  }
  results.push({ name, result: JSON.parse(resultJson), at: new Date().toISOString() });
  fs.writeFileSync(logFile, JSON.stringify(results, null, 2));
  console.log("recorded", name);
  process.exit(0);
}

console.log(JSON.stringify({ names, count: names.length }));
