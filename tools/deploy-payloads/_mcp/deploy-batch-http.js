#!/usr/bin/env node
/**
 * Deploy edge functions from _prepared/*.json via Supabase MCP HTTP.
 * Reads prepared payloads with full security.ts (14287 bytes).
 */
const fs = require("fs");
const path = require("path");

const preparedDir = path.join(__dirname, "_prepared");
const resultsFile = path.join(__dirname, "_deploy-results.json");

const order = process.argv.slice(2).length
  ? process.argv.slice(2)
  : [
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

const MCP_URL =
  "https://mcp.supabase.com/mcp?project_ref=rkfveqzktfmgegtsoxlf&read_only=false&features=functions";

function loadPrepared(name) {
  const p = path.join(preparedDir, `${name}.json`);
  const args = JSON.parse(fs.readFileSync(p, "utf8"));
  const sec = args.files.find((f) => f.name.includes("security"));
  if (!sec || sec.content.length !== 14287) {
    throw new Error(
      `${name}: security.ts must be 14287 bytes, got ${sec ? sec.content.length : "missing"}`,
    );
  }
  return args;
}

async function mcpCall(toolName, args, sessionId) {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
  };
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;

  const body = {
    jsonrpc: "2.0",
    id: Date.now(),
    method: "tools/call",
    params: { name: toolName, arguments: args },
  };

  const res = await fetch(MCP_URL, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });

  const text = await res.text();
  let sid = res.headers.get("mcp-session-id") || sessionId;

  // Parse SSE or JSON response
  let result = null;
  let error = null;
  for (const line of text.split("\n")) {
    if (line.startsWith("data: ")) {
      try {
        const parsed = JSON.parse(line.slice(6));
        if (parsed.error) error = parsed.error;
        if (parsed.result) result = parsed.result;
      } catch {}
    }
  }
  if (!result && !error) {
    try {
      const parsed = JSON.parse(text);
      if (parsed.error) error = parsed.error;
      if (parsed.result) result = parsed.result;
    } catch {
      error = { message: text.slice(0, 500) };
    }
  }

  return { status: res.status, sessionId: sid, result, error, raw: text.slice(0, 300) };
}

async function initSession() {
  const res = await fetch(MCP_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json, text/event-stream",
    },
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: 1,
      method: "initialize",
      params: {
        protocolVersion: "2024-11-05",
        capabilities: {},
        clientInfo: { name: "deploy-batch", version: "1.0.0" },
      },
    }),
  });
  const sid = res.headers.get("mcp-session-id");
  return sid;
}

async function main() {
  let sessionId = null;
  try {
    sessionId = await initSession();
  } catch (e) {
    console.error("init failed", e);
  }

  const results = [];

  for (const name of order) {
    process.stderr.write(`Deploying ${name}...\n`);
    try {
      const args = loadPrepared(name);
      const r = await mcpCall("deploy_edge_function", args, sessionId);
      sessionId = r.sessionId || sessionId;
      const ok = !r.error && r.status < 400;
      const errMsg = r.error
        ? typeof r.error === "string"
          ? r.error
          : r.error.message || JSON.stringify(r.error)
        : ok
          ? null
          : `HTTP ${r.status}: ${r.raw}`;
      results.push({
        name,
        deployed: ok ? "yes" : "no",
        verify_jwt: args.verify_jwt,
        error: errMsg,
        result: r.result,
      });
      process.stderr.write(`${name}: ${ok ? "OK" : "FAIL"} ${errMsg || ""}\n`);
    } catch (err) {
      results.push({
        name,
        deployed: "no",
        verify_jwt: null,
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
