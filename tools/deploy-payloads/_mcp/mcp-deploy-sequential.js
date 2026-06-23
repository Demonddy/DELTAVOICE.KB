#!/usr/bin/env node
/**
 * Sequential deploy helper: stages each function's prepared args and records results.
 * Agent calls CallMcpTool deploy_edge_function with _current-args.json after each stage.
 *
 * Usage:
 *   node mcp-deploy-sequential.js next     -> prints next function name or DONE
 *   node mcp-deploy-sequential.js stage <name> -> writes _current-args.json
 *   node mcp-deploy-sequential.js record <name> <ok|fail> [error]
 */
const fs = require("fs");
const path = require("path");

const preparedDir = path.join(__dirname, "_prepared");
const currentArgs = path.join(__dirname, "_current-args.json");
const logFile = path.join(__dirname, "_deploy-results.json");
const order = [
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
];

function loadLog() {
  if (!fs.existsSync(logFile)) return [];
  return JSON.parse(fs.readFileSync(logFile, "utf8"));
}

function saveLog(log) {
  fs.writeFileSync(logFile, JSON.stringify(log, null, 2));
}

function stage(name) {
  const src = path.join(preparedDir, `${name}.json`);
  const args = JSON.parse(fs.readFileSync(src, "utf8"));
  const sec = args.files.find((f) => f.name.includes("security"));
  if (!sec || sec.content.length !== 14287) {
    throw new Error(
      `${name}: security.ts must be 14287 bytes, got ${sec ? sec.content.length : "missing"}`,
    );
  }
  fs.writeFileSync(currentArgs, JSON.stringify(args), "utf8");
  return args;
}

const cmd = process.argv[2];

if (cmd === "stage") {
  const name = process.argv[3];
  const args = stage(name);
  console.log(
    JSON.stringify({
      name: args.name,
      verify_jwt: args.verify_jwt,
      sec_bytes: args.files.find((f) => f.name.includes("security")).content.length,
    }),
  );
  process.exit(0);
}

if (cmd === "record") {
  const name = process.argv[3];
  const ok = process.argv[4] === "ok";
  const error = process.argv[5] || null;
  const log = loadLog();
  const args = JSON.parse(fs.readFileSync(currentArgs, "utf8"));
  log.push({
    name,
    deployed: ok ? "yes" : "no",
    verify_jwt: args.verify_jwt,
    error,
    at: new Date().toISOString(),
  });
  saveLog(log);
  console.log("recorded", name, ok ? "ok" : "fail");
  process.exit(0);
}

if (cmd === "next") {
  const log = loadLog();
  const done = new Set(log.map((r) => r.name));
  const next = order.find((n) => !done.has(n));
  console.log(next || "DONE");
  process.exit(0);
}

if (cmd === "summary") {
  console.log(JSON.stringify(loadLog(), null, 2));
  process.exit(0);
}

console.error("Usage: next | stage <name> | record <name> <ok|fail> [error] | summary");
process.exit(1);
