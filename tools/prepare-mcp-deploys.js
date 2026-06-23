#!/usr/bin/env node
/**
 * Deploy edge functions sequentially via user-supabase MCP HTTP API.
 * Requires Cursor MCP OAuth token in CURSOR_MCP_TOKEN or reads from env.
 * Fallback: prints args path for manual MCP deploy.
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

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

const base = path.join(__dirname, 'deploy-payloads', '_mcp');
const resultsPath = path.join(base, 'deploy-results.json');
const startIdx = Number(process.argv[2] || 0);
const endIdx = process.argv[3] ? Number(process.argv[3]) : ORDER.length;

const results = fs.existsSync(resultsPath)
  ? JSON.parse(fs.readFileSync(resultsPath, 'utf8'))
  : [];

for (let i = startIdx; i < endIdx && i < ORDER.length; i++) {
  const name = ORDER[i];
  const argsPath = path.join(base, `${name}.args.json`);
  const args = JSON.parse(fs.readFileSync(argsPath, 'utf8'));
  const outPath = path.join(base, '_current.json');
  fs.writeFileSync(outPath, JSON.stringify(args), 'utf8');
  console.log(`READY\t${name}\tverify_jwt=${args.verify_jwt}\tfiles=${args.files.length}\tpath=${outPath}`);
}
