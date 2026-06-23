#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const FUNCTIONS = [
  { name: 'password-reset', verify_jwt: false },
  { name: 'stripe-webhook', verify_jwt: false },
  { name: 'get-deepgram-key', verify_jwt: false },
  { name: 'admin-dashboard', verify_jwt: true },
  { name: 'create-checkout', verify_jwt: true },
  { name: 'customer-portal', verify_jwt: true },
  { name: 'check-subscription', verify_jwt: true },
  { name: 'ai-chat', verify_jwt: true },
  { name: 'translate-text', verify_jwt: true },
  { name: 'writing-tool', verify_jwt: true },
  { name: 'complete-voice-workflow', verify_jwt: true },
  { name: 'voice-to-text', verify_jwt: true },
  { name: 'voice-conversion', verify_jwt: true },
  { name: 'create-voice-clone', verify_jwt: true },
  { name: 'free-translate-text', verify_jwt: true },
  { name: 'free-voice-translate', verify_jwt: true },
];

const root = path.join(__dirname, '..');
const outDir = path.join(__dirname, 'deploy-payloads', '_mcp', '_live');
fs.mkdirSync(outDir, { recursive: true });

const securityPath = path.join(root, 'supabase', 'functions', '_shared', 'security.ts');
const securityContent = fs.readFileSync(securityPath, 'utf8');

for (const fn of FUNCTIONS) {
  const indexPath = path.join(root, 'supabase', 'functions', fn.name, 'index.ts');
  const indexContent = fs.readFileSync(indexPath, 'utf8');
  const files = [{ name: 'index.ts', content: indexContent }];
  if (indexContent.includes('_shared/security.ts')) {
    files.push({ name: '../_shared/security.ts', content: securityContent });
  }
  const payload = {
    name: fn.name,
    entrypoint_path: 'index.ts',
    verify_jwt: fn.verify_jwt,
    files,
  };
  fs.writeFileSync(
    path.join(outDir, `${fn.name}.json`),
    JSON.stringify(payload),
    'utf8',
  );
  console.log(`packed ${fn.name}`);
}
