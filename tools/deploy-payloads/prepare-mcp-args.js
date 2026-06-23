#!/usr/bin/env node
/**
 * Deploy all edge functions by reading deploy-payloads/*.json
 * and calling user-supabase MCP deploy_edge_function via Cursor agent tool bridge.
 * This script writes one args file per function for MCP consumption.
 */
const fs = require("fs");
const path = require("path");

const dir = __dirname;
const outDir = path.join(dir, "_mcp");
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

fs.mkdirSync(outDir, { recursive: true });

for (const name of order) {
  const payload = JSON.parse(
    fs.readFileSync(path.join(dir, `${name}.json`), "utf8"),
  );
  const args = {
    name: payload.name,
    entrypoint_path: payload.entrypoint_path,
    verify_jwt: payload.verify_jwt,
    files: payload.files,
  };
  fs.writeFileSync(
    path.join(outDir, `${name}.args.json`),
    JSON.stringify(args),
  );
}

console.log(`Prepared ${order.length} MCP arg files in ${outDir}`);
