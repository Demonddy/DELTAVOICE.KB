#!/usr/bin/env node
/**
 * Prepare MCP deploy call JSON files for all edge functions.
 * Usage: node tools/prepare-mcp-call-files.js
 */
const fs = require("fs");
const path = require("path");

const ORDER = [
  "password-reset",
  "stripe-webhook",
  "get-deepgram-key",
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
];

const base = path.join(__dirname, "deploy-payloads", "_mcp");

for (const name of ORDER) {
  const args = JSON.parse(fs.readFileSync(path.join(base, `${name}.args.json`), "utf8"));
  for (const file of args.files) {
    if (file.content.charCodeAt(0) === 0xfeff) {
      file.content = file.content.slice(1);
    }
  }
  const out = {
    name: args.name,
    entrypoint_path: args.entrypoint_path,
    verify_jwt: args.verify_jwt,
    files: args.files,
  };
  fs.writeFileSync(path.join(base, `_mcp-call-${name}.json`), JSON.stringify(out));
  console.log(`prepared ${name}`);
}
