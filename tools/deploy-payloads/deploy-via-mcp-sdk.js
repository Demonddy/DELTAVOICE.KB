#!/usr/bin/env node
/**
 * Deploy all edge functions by reading deploy-payloads/*.json
 * and invoking user-supabase MCP deploy_edge_function via @modelcontextprotocol/sdk.
 *
 * Requires: npm install @modelcontextprotocol/sdk (run once in this directory)
 * Requires: SUPABASE_ACCESS_TOKEN env var OR Cursor MCP already configured
 *
 * Usage: node deploy-via-mcp-sdk.js [function-name|all]
 */
const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");

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
  let Client, StdioClientTransport;
  try {
    ({ Client } = require("@modelcontextprotocol/sdk/client/index.js"));
    ({
      StdioClientTransport,
    } = require("@modelcontextprotocol/sdk/client/stdio.js"));
  } catch {
    console.error(
      "Missing @modelcontextprotocol/sdk. Run: npm install @modelcontextprotocol/sdk",
    );
    process.exit(1);
  }

  const token = process.env.SUPABASE_ACCESS_TOKEN;
  if (!token) {
    console.error("SUPABASE_ACCESS_TOKEN not set");
    process.exit(1);
  }

  const transport = new StdioClientTransport({
    command: "npx",
    args: [
      "-y",
      "@supabase/mcp-server-supabase@latest",
      "--access-token",
      token,
    ],
  });

  const client = new Client(
    { name: "deploy-script", version: "1.0.0" },
    { capabilities: {} },
  );
  await client.connect(transport);

  const only = process.argv[2] || "all";
  const names = only === "all" ? order : [only];
  const results = [];

  for (const name of names) {
    const args = loadArgs(name);
    process.stderr.write(`Deploying ${name}...\n`);
    try {
      const result = await client.callTool({
        name: "deploy_edge_function",
        arguments: args,
      });
      results.push({ name, ok: true, result });
    } catch (err) {
      results.push({ name, ok: false, error: String(err) });
    }
  }

  await client.close();
  console.log(JSON.stringify(results, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
