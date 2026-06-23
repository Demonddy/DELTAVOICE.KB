#!/usr/bin/env node
/**
 * Prepare deploy args files for all functions in order.
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

const base = path.join(__dirname, 'deploy-payloads', '_mcp');
const outDir = path.join(base, '_prepared');
fs.mkdirSync(outDir, { recursive: true });

for (const name of ORDER) {
  const args = JSON.parse(fs.readFileSync(path.join(base, `${name}.args.json`), 'utf8'));
  fs.writeFileSync(path.join(outDir, `${name}.json`), JSON.stringify(args), 'utf8');
  console.log(name, args.verify_jwt, args.files.length);
}
