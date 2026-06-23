#!/usr/bin/env node
/**
 * Reads MCP arg files and prints deploy instructions.
 * Agent should call CallMcpTool deploy_edge_function for each.
 */
const fs = require("fs");
const path = require("path");

const mcpDir = path.join(__dirname, "_mcp");
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
  console.error("Usage: node mcp-deploy-one.js <function-name>");
  process.exit(1);
}

const file = path.join(mcpDir, `${fn}.args.json`);
if (!fs.existsSync(file)) {
  console.error(`Missing ${file}`);
  process.exit(1);
}

process.stdout.write(fs.readFileSync(file, "utf8"));
