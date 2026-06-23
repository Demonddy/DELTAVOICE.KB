#!/usr/bin/env node
/**
 * Deploy all edge functions by reading deploy-*.json payloads.
 * Requires SUPABASE_ACCESS_TOKEN in environment.
 */
const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

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

const root = path.join(__dirname, "..");
const results = [];

for (const name of ORDER) {
  process.stderr.write(`Preparing ${name}...\n`);
  const json = execSync(`node tools/mcp-deploy-read.js ${name}`, {
    cwd: root,
    encoding: "utf8",
  });
  const args = JSON.parse(json);
  fs.writeFileSync(
    path.join(root, "tools", "deploy-payloads", "_mcp", `_last-${name}.json"),
    JSON.stringify(args),
  );
  results.push({ name, bytes: json.length, verify_jwt: args.verify_jwt });
}

console.log(JSON.stringify(results, null, 2));
