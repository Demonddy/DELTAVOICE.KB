#!/usr/bin/env node
/**
 * Deploy edge functions from _prepared/*.json via user-supabase MCP HTTP endpoint.
 * Used when agent needs to batch-deploy; outputs results JSON to stdout.
 */
const fs = require("fs");
const path = require("path");

const preparedDir = path.join(__dirname, "_prepared");
const resultsFile = path.join(__dirname, "_deploy-results.json");

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

function loadPrepared(name) {
  const p = path.join(preparedDir, `${name}.json`);
  return JSON.parse(fs.readFileSync(p, "utf8"));
}

async function deployOne(name) {
  const args = loadPrepared(name);
  const sec = args.files.find((f) => f.name.includes("security"));
  if (!sec || sec.content.length !== 14287) {
    throw new Error(
      `security.ts must be full 14287 bytes, got ${sec ? sec.content.length : 0}`,
    );
  }

  // Supabase Management API for edge functions deploy
  const projectRef = "rkfveqzktfmgegtsoxlf";
  const mcpUrl =
    process.env.SUPABASE_MCP_URL ||
    `https://mcp.supabase.com/mcp?project_ref=${projectRef}&read_only=false&features=functions`;

  const token = process.env.SUPABASE_ACCESS_TOKEN;
  if (!token) {
    return {
      name,
      ok: false,
      verify_jwt: args.verify_jwt,
      error: "SUPABASE_ACCESS_TOKEN not set - use CallMcpTool instead",
    };
  }

  const res = await fetch(mcpUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: 1,
      method: "tools/call",
      params: {
        name: "deploy_edge_function",
        arguments: args,
      },
    }),
  });

  const body = await res.text();
  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch {
    parsed = { raw: body };
  }

  const err =
    parsed.error?.message ||
    (parsed.result?.isError ? JSON.stringify(parsed.result) : null);

  return {
    name,
    ok: res.ok && !err && !parsed.error,
    verify_jwt: args.verify_jwt,
    status: res.status,
    result: parsed.result || parsed,
    error: err || (res.ok ? null : `HTTP ${res.status}`),
  };
}

async function main() {
  const only = process.argv[2];
  const names = only ? [only] : order;
  const results = [];

  for (const name of names) {
    process.stderr.write(`Deploying ${name}...\n`);
    try {
      const r = await deployOne(name);
      results.push(r);
      process.stderr.write(
        `${name}: ${r.ok ? "OK" : "FAIL"} ${r.error || ""}\n`,
      );
    } catch (err) {
      results.push({
        name,
        ok: false,
        error: String(err),
      });
    }
  }

  fs.writeFileSync(resultsFile, JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
