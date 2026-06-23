#!/usr/bin/env node
/**
 * Deploy all edge functions sequentially via user-supabase MCP deploy_edge_function.
 * Reads JSON payloads from deploy-payloads/<name>.json and writes results to _deploy-results.json.
 *
 * Usage: node deploy-all-mcp.js
 * Requires: npm install @modelcontextprotocol/sdk (in this directory)
 * Requires: SUPABASE_ACCESS_TOKEN env var
 */
const fs = require('fs');
const path = require('path');

const ORDER = [
  'password-reset',
  'stripe-webhook',
  'admin-dashboard',
  'create-checkout',
  'customer-portal',
  'check-subscription',
  'ai-chat',
  'translate-text',
  'writing-tool',
  'complete-voice-workflow',
  'voice-to-text',
  'voice-conversion',
  'create-voice-clone',
  'free-translate-text',
  'free-voice-translate',
  'get-deepgram-key',
];

function loadArgs(name) {
  const payload = JSON.parse(
    fs.readFileSync(path.join(__dirname, `${name}.json`), 'utf8'),
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
    ({ Client } = require('@modelcontextprotocol/sdk/client/index.js'));
    ({ StdioClientTransport } = require('@modelcontextprotocol/sdk/client/stdio.js'));
  } catch {
    console.error('Run: npm install @modelcontextprotocol/sdk in tools/deploy-payloads');
    process.exit(1);
  }

  const token = process.env.SUPABASE_ACCESS_TOKEN;
  if (!token) {
    console.error('SUPABASE_ACCESS_TOKEN not set');
    process.exit(1);
  }

  const transport = new StdioClientTransport({
    command: 'npx',
    args: ['-y', '@supabase/mcp-server-supabase@latest', '--access-token', token],
  });

  const client = new Client({ name: 'deploy-all', version: '1.0.0' }, { capabilities: {} });
  await client.connect(transport);

  const results = [];
  for (const name of ORDER) {
    const args = loadArgs(name);
    process.stderr.write(`Deploying ${name} (verify_jwt=${args.verify_jwt})...\n`);
    try {
      const result = await client.callTool({
        name: 'deploy_edge_function',
        arguments: args,
      });
      const text = result.content?.map((c) => c.text).join('') || JSON.stringify(result);
      let parsed;
      try { parsed = JSON.parse(text); } catch { parsed = { raw: text }; }
      results.push({ name, ok: true, verify_jwt: args.verify_jwt, result: parsed });
      process.stderr.write(`  OK ${name}\n`);
    } catch (err) {
      results.push({ name, ok: false, verify_jwt: args.verify_jwt, error: String(err) });
      process.stderr.write(`  FAIL ${name}: ${err}\n`);
    }
  }

  await client.close();
  const outPath = path.join(__dirname, '_deploy-results.json');
  fs.writeFileSync(outPath, JSON.stringify(results, null, 2));
  console.log(JSON.stringify({ deployed: results.filter((r) => r.ok).length, total: ORDER.length, outPath }));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
