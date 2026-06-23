#!/usr/bin/env node
/**
 * Deploy prepared edge functions via user-supabase MCP HTTP using Cursor session.
 * Reads args from _deploy-out/{name}.args-only.json
 */
const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, '_deploy-out');
const MCP_URL = 'https://mcp.supabase.com/mcp?project_ref=rkfveqzktfmgegtsoxlf&read_only=false&features=functions';
const names = process.argv.slice(2);
if (!names.length) {
  console.error('Usage: node deploy-from-args-files.js <name> [...]');
  process.exit(1);
}

async function mcpCall(toolName, args, sessionId) {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json, text/event-stream',
  };
  if (sessionId) headers['Mcp-Session-Id'] = sessionId;

  const res = await fetch(MCP_URL, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: Date.now(),
      method: 'tools/call',
      params: { name: toolName, arguments: args },
    }),
  });

  const text = await res.text();
  const sid = res.headers.get('mcp-session-id') || sessionId;
  let result = null;
  let error = null;
  for (const line of text.split('\n')) {
    if (line.startsWith('data: ')) {
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
  return { status: res.status, sessionId: sid, result, error };
}

async function initSession() {
  const res = await fetch(MCP_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream',
    },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: 1,
      method: 'initialize',
      params: {
        protocolVersion: '2024-11-05',
        capabilities: {},
        clientInfo: { name: 'deploy-from-args-files', version: '1.0.0' },
      },
    }),
  });
  return res.headers.get('mcp-session-id');
}

function parseVersion(result) {
  try {
    const text = result?.content?.[0]?.text;
    if (!text) return null;
    const parsed = JSON.parse(text);
    return parsed.version ?? null;
  } catch {
    return null;
  }
}

async function main() {
  let sessionId = null;
  try {
    sessionId = await initSession();
  } catch (e) {
    console.error('init failed', e);
  }

  const results = [];
  for (const name of names) {
    const file = path.join(outDir, `${name}.args-only.json`);
    const args = JSON.parse(fs.readFileSync(file, 'utf8'));
    const r = await mcpCall('deploy_edge_function', args, sessionId);
    sessionId = r.sessionId || sessionId;
    const errMsg = r.error
      ? typeof r.error === 'string'
        ? r.error
        : r.error.message || JSON.stringify(r.error)
      : r.status >= 400
        ? `HTTP ${r.status}`
        : null;
    const version = parseVersion(r.result);
    results.push({
      name,
      ok: !errMsg,
      version,
      verify_jwt: args.verify_jwt,
      error: errMsg,
    });
  }

  const logFile = path.join(outDir, 'deploy-results.json');
  fs.writeFileSync(logFile, JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
