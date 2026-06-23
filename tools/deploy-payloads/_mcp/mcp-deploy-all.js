#!/usr/bin/env node
/**
 * Deploy edge functions via Supabase MCP HTTP using args from _call-{name}.json.
 * Requires Cursor MCP OAuth session - use CallMcpTool from agent instead if this fails.
 * Usage: node mcp-deploy-all.js [function-name|all]
 */
const fs = require("fs");
const path = require("path");

const ORDER = [
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

const dir = __dirname;
const resultsFile = path.join(dir, "_deploy-final-results.json");

function loadArgs(name) {
  const file = path.join(dir, `_call-${name}.json`);
  const args = JSON.parse(fs.readFileSync(file, "utf8"));
  const sec = args.files.find((f) => f.name.includes("security"));
  if (!sec || sec.content.length !== 14287) {
    throw new Error(`${name}: security.ts must be 14287 bytes, got ${sec ? sec.content.length : 0}`);
  }
  return args;
}

async function deployOne(name) {
  const args = loadArgs(name);
  const mcpUrl =
    "https://mcp.supabase.com/mcp?project_ref=rkfveqzktfmgegtsoxlf&read_only=false&features=functions";
  const token = process.env.SUPABASE_ACCESS_TOKEN;
  if (!token) {
    return { name, ok: false, verify_jwt: args.verify_jwt, error: "SUPABASE_ACCESS_TOKEN not set" };
  }
  const res = await fetch(mcpUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      Accept: "application/json, text/event-stream",
    },
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: 1,
      method: "tools/call",
      params: { name: "deploy_edge_function", arguments: args },
    }),
  });
  const text = await res.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 500) };
  }
  const err = parsed.error?.message || (parsed.result?.isError ? JSON.stringify(parsed.result) : null);
  return {
    name,
    ok: res.ok && !err && !parsed.error,
    verify_jwt: args.verify_jwt,
    version: parsed.result?.content?.[0]?.text ? JSON.parse(parsed.result.content[0].text).version : undefined,
    error: err || (res.ok ? null : `HTTP ${res.status}`),
  };
}

async function main() {
  const only = process.argv[2];
  const names = only && only !== "all" ? [only] : ORDER;
  const results = [];
  for (const name of names) {
    process.stderr.write(`Deploying ${name}...\n`);
    try {
      const r = await deployOne(name);
      results.push(r);
      process.stderr.write(`${name}: ${r.ok ? "OK" : "FAIL"} ${r.error || ""}\n`);
    } catch (e) {
      results.push({ name, ok: false, error: String(e) });
    }
  }
  fs.writeFileSync(resultsFile, JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
