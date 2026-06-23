#!/usr/bin/env node
/**
 * Loads deploy args from _call-{name}.json for agent CallMcpTool invocations.
 * Usage: node agent-deploy-loop.js load <name>   -> writes _agent-current.json, prints meta
 *        node agent-deploy-loop.js record <name> <ok|fail> [error]
 */
const fs = require("fs");
const path = require("path");

const dir = __dirname;
const current = path.join(dir, "_agent-current.json");
const logFile = path.join(dir, "_agent-deploy-log.json");
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
  return fs.existsSync(logFile) ? JSON.parse(fs.readFileSync(logFile, "utf8")) : [];
}

function saveLog(log) {
  fs.writeFileSync(logFile, JSON.stringify(log, null, 2));
}

const cmd = process.argv[2];

if (cmd === "load") {
  const name = process.argv[3];
  const file = path.join(dir, `_call-${name}.json`);
  const args = JSON.parse(fs.readFileSync(file, "utf8"));
  const sec = args.files.find((f) => f.name.includes("security"));
  if (!sec || sec.content.length !== 14287) {
    console.error(`security.ts must be 14287 bytes, got ${sec ? sec.content.length : 0}`);
    process.exit(1);
  }
  fs.writeFileSync(current, JSON.stringify(args), "utf8");
  console.log(JSON.stringify({ name, verify_jwt: args.verify_jwt, secLen: sec.content.length, file: current }));
  process.exit(0);
}

if (cmd === "record") {
  const name = process.argv[3];
  const ok = process.argv[4] === "ok";
  const error = process.argv[5] || null;
  const args = JSON.parse(fs.readFileSync(current, "utf8"));
  const log = loadLog();
  log.push({ name, deployed: ok ? "yes" : "no", verify_jwt: args.verify_jwt, error, at: new Date().toISOString() });
  saveLog(log);
  console.log("recorded", name, ok ? "ok" : "fail");
  process.exit(0);
}

if (cmd === "next") {
  const log = loadLog();
  const done = new Set(log.filter((r) => r.deployed === "yes").map((r) => r.name));
  const next = order.find((n) => !done.has(n));
  console.log(next || "DONE");
  process.exit(0);
}

console.error("Usage: load <name> | record <name> <ok|fail> [error] | next");
process.exit(1);
