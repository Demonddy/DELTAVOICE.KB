#!/usr/bin/env node
/**
 * Deploy all edge functions via Supabase MCP deploy payloads.
 * Prints one JSON line per function for agent MCP invocation:
 *   DEPLOY_JSON {"name":"...","entrypoint_path":"...","verify_jwt":...,"files":[...]}
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
const only = process.argv[2];

for (const name of ORDER) {
  if (only && only !== name) continue;
  const p = path.join(base, `deploy-${name}.json`);
  const payload = JSON.parse(fs.readFileSync(p, "utf8"));
  console.log(`DEPLOY_JSON ${JSON.stringify(payload)}`);
}
