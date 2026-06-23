#!/usr/bin/env node
/**
 * Deploy all edge functions from _live payloads via sequential MCP-style logging.
 * Agent should call deploy_edge_function MCP with output from: node deploy-live-batch.js <name>
 */
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

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
const resultsPath = path.join(liveDir, '_deploy-results.json');

function loadPayload(name) {
  return JSON.parse(fs.readFileSync(path.join(liveDir, `${name}.json`), 'utf8'));
}

function saveResults(results) {
  fs.writeFileSync(resultsPath, JSON.stringify(results, null, 2), 'utf8');
}

function loadResults() {
  if (!fs.existsSync(resultsPath)) return {};
  try {
    return JSON.parse(fs.readFileSync(resultsPath, 'utf8'));
  } catch {
    return {};
  }
}

const cmd = process.argv[2];
const fn = process.argv[3];

if (cmd === 'get-args') {
  const p = loadPayload(fn);
  process.stdout.write(JSON.stringify({
    name: p.name,
    entrypoint_path: p.entrypoint_path,
    verify_jwt: p.verify_jwt,
    files: p.files,
  }));
  process.exit(0);
}

if (cmd === 'record') {
  const results = loadResults();
  results[fn] = {
    status: process.argv[4],
    version: process.argv[5] || null,
    error: process.argv[6] || null,
    at: new Date().toISOString(),
  };
  saveResults(results);
  process.exit(0);
}

if (cmd === 'summary') {
  const results = loadResults();
  console.log('| Function | Version/Status | Error |');
  console.log('|----------|----------------|-------|');
  for (const name of ORDER) {
    const r = results[name] || { status: 'not deployed', version: '-', error: 'not attempted' };
    const ver = r.version ? `v${r.version} ${r.status}` : r.status;
    console.log(`| ${name} | ${ver} | ${r.error || '-'} |`);
  }
  process.exit(0);
}

if (cmd === 'pending') {
  const results = loadResults();
  const pending = ORDER.filter((n) => !results[n] || results[n].status !== 'success');
  pending.forEach((n) => console.log(n));
  process.exit(0);
}

console.error('Usage: deploy-all-live.js get-args|record|summary|pending <name> [status version error]');
process.exit(1);
