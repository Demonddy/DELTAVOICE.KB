#!/usr/bin/env node
/**
 * Deploy edge functions by reading deploy-payloads/*.json and invoking
 * the Supabase MCP deploy_edge_function tool via Cursor's MCP HTTP endpoint.
 * Falls back to printing JSON args when MCP URL/token unavailable.
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

function loadArgs(name) {
  const payload = JSON.parse(
    fs.readFileSync(path.join(dir, `${name}.json`), "utf8"),
  );
  return {
    name: payload.name,
    entrypoint_path: payload.entrypoint_path,
    verify_jwt: payload.verify_jwt,
    files: payload.files,
  };
}

async function main() {
  const only = process.argv[2];
  const names = only && only !== "all" ? [only] : order;
  const results = [];

  for (const name of names) {
    const args = loadArgs(name);
    process.stderr.write(`Deploying ${name}...\n`);
    try {
      // Use dynamic import for fetch in Node 18+
      const res = await fetch("cursor://mcp/user-supabase/deploy_edge_function", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(args),
      }).catch(() => null);

      if (!res) {
        results.push({ name, ok: false, error: "MCP fetch unavailable; use CallMcpTool" });
        continue;
      }
      const body = await res.json();
      results.push({ name, ok: !body.error, body });
    } catch (err) {
      results.push({ name, ok: false, error: String(err) });
    }
  }

  console.log(JSON.stringify(results, null, 2));
}

main();
