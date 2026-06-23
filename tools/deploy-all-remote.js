#!/usr/bin/env node
/**
 * Deploy all edge functions via Supabase Management API (same payload as MCP).
 * Usage: SUPABASE_ACCESS_TOKEN=sbp_xxx node tools/deploy-all-remote.js
 */
const fs = require("fs");
const path = require("path");

const PROJECT_REF = "rkfveqzktfmgegtsoxlf";
const BASE = path.join(__dirname, "deploy-payloads", "_mcp");

const ORDER = [
  { name: "password-reset", verify_jwt: false },
  { name: "stripe-webhook", verify_jwt: false },
  { name: "get-deepgram-key", verify_jwt: false },
  { name: "admin-dashboard", verify_jwt: true },
  { name: "create-checkout", verify_jwt: true },
  { name: "customer-portal", verify_jwt: true },
  { name: "check-subscription", verify_jwt: true },
  { name: "ai-chat", verify_jwt: true },
  { name: "translate-text", verify_jwt: true },
  { name: "writing-tool", verify_jwt: true },
  { name: "complete-voice-workflow", verify_jwt: true },
  { name: "voice-to-text", verify_jwt: true },
  { name: "voice-conversion", verify_jwt: true },
  { name: "create-voice-clone", verify_jwt: true },
  { name: "free-translate-text", verify_jwt: true },
  { name: "free-voice-translate", verify_jwt: true },
];

async function deployOne(name, verify_jwt) {
  const invokePath = path.join(BASE, `_invoke-${name}.json`);
  const deployPath = path.join(BASE, `deploy-${name}.json`);
  const payloadPath = fs.existsSync(invokePath)
    ? invokePath
    : fs.existsSync(deployPath)
      ? deployPath
      : path.join(BASE, `${name}.args.json`);
  const payload = JSON.parse(fs.readFileSync(payloadPath, "utf8"));

  const metadata = {
    entrypoint_path: payload.entrypoint_path,
    name: payload.name,
    verify_jwt,
  };

  const form = new FormData();
  form.append("metadata", JSON.stringify(metadata));

  for (const file of payload.files) {
    const blob = new Blob([file.content], { type: "application/typescript" });
    form.append("file", blob, file.name.replace(/^\.\.\/_shared\//, "_shared/"));
  }

  const url = `https://api.supabase.com/v1/projects/${PROJECT_REF}/functions/deploy?slug=${encodeURIComponent(name)}`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${process.env.SUPABASE_ACCESS_TOKEN}`,
    },
    body: form,
  });

  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${res.status} ${text.slice(0, 400)}`);
  }
  return JSON.parse(text);
}

async function main() {
  const token = process.env.SUPABASE_ACCESS_TOKEN;
  if (!token) {
    console.error("Set SUPABASE_ACCESS_TOKEN (supabase login / dashboard access token)");
    process.exit(1);
  }

  const results = [];
  for (const fn of ORDER) {
    process.stderr.write(`Deploying ${fn.name}...\n`);
    try {
      const result = await deployOne(fn.name, fn.verify_jwt);
      results.push({ name: fn.name, ok: true, version: result.version, id: result.id });
      process.stderr.write(`  OK v${result.version}\n`);
    } catch (err) {
      results.push({ name: fn.name, ok: false, error: String(err) });
      process.stderr.write(`  FAIL ${err}\n`);
    }
  }

  console.log(JSON.stringify(results, null, 2));
  const failed = results.filter((r) => !r.ok);
  process.exit(failed.length ? 1 : 0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
