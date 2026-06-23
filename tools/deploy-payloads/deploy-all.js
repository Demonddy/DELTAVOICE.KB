#!/usr/bin/env node
/**
 * Reads deploy-payloads/*.json and prints each payload as a single JSON line
 * for downstream MCP deploy_edge_function calls.
 */
const fs = require("fs");
const path = require("path");

const dir = __dirname;
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

const fn = process.argv[2];
if (!fn) {
  console.error("Usage: node deploy-all.js <function-name|all>");
  process.exit(1);
}

const names = fn === "all" ? order : [fn];
for (const name of names) {
  const file = path.join(dir, `${name}.json`);
  const payload = JSON.parse(fs.readFileSync(file, "utf8"));
  const args = {
    name: payload.name,
    entrypoint_path: payload.entrypoint_path,
    verify_jwt: payload.verify_jwt,
    files: payload.files,
  };
  process.stdout.write(JSON.stringify(args) + "\n");
}
