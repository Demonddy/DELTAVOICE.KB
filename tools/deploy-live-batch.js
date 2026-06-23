#!/usr/bin/env node
/**
 * Load deploy payloads from _live/ for MCP deploy_edge_function calls.
 * Usage: node deploy-live-batch.js [function-name]
 * Without args: prints ordered function names (one per line).
 */
const fs = require('fs');
const path = require('path');

const ORDER = [
  'password-reset',
  'stripe-webhook',
  'get-deepgram-key',
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
];

const liveDir = path.join(__dirname, 'deploy-payloads', '_mcp', '_live');
const logPath = path.join(liveDir, '_deploy-log.json');

function loadPayload(name) {
  const file = path.join(liveDir, `${name}.json`);
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function appendLog(entry) {
  let log = [];
  if (fs.existsSync(logPath)) {
    try {
      log = JSON.parse(fs.readFileSync(logPath, 'utf8'));
    } catch {
      log = [];
    }
  }
  const idx = log.findIndex((e) => e.name === entry.name);
  if (idx >= 0) log[idx] = entry;
  else log.push(entry);
  fs.writeFileSync(logPath, JSON.stringify(log, null, 2), 'utf8');
}

const fn = process.argv[2];
const cmd = process.argv[3];

if (!fn) {
  ORDER.forEach((n) => console.log(n));
  process.exit(0);
}

if (cmd === 'log') {
  const status = process.argv[4] || 'unknown';
  const version = process.argv[5] || null;
  const error = process.argv[6] || null;
  appendLog({ name: fn, status, version, error, at: new Date().toISOString() });
  process.exit(0);
}

const payload = loadPayload(fn);
process.stdout.write(
  JSON.stringify({
    name: payload.name,
    entrypoint_path: payload.entrypoint_path,
    verify_jwt: payload.verify_jwt,
    files: payload.files,
  }),
);
