#!/usr/bin/env node
/**
 * Deploy all edge functions sequentially via user-supabase MCP.
 * Reads args from deploy-payloads/_mcp/*.args.json and writes results.
 * Usage: node deploy-all-mcp.js <function-name>
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

const fn = process.argv[2];
const names = fn ? [fn] : ORDER;
const base = path.join(__dirname, 'deploy-payloads', '_mcp');
const outDir = path.join(base, '_deploy-out');
fs.mkdirSync(outDir, { recursive: true });

for (const name of names) {
  const argsPath = path.join(base, `${name}.args.json`);
  const args = JSON.parse(fs.readFileSync(argsPath, 'utf8'));
  const outPath = path.join(outDir, `${name}.args.json`);
  fs.writeFileSync(outPath, JSON.stringify(args), 'utf8');
  console.log(`PREPARED ${name} verify_jwt=${args.verify_jwt} files=${args.files.length}`);
}
