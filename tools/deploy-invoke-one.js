#!/usr/bin/env node
/**
 * Deploy one edge function via Supabase Management API.
 * Reads tools/deploy-payloads/_mcp/_invoke-<name>.json
 * Requires SUPABASE_ACCESS_TOKEN env var OR pass token as 2nd arg.
 */
const fs = require("fs");
const path = require("path");

const PROJECT_REF = "rkfveqzktfmgegtsoxlf";
const name = process.argv[2];
const token = process.argv[3] || process.env.SUPABASE_ACCESS_TOKEN;

if (!name) {
  console.error("Usage: node tools/deploy-invoke-one.js <function-name> [access-token]");
  process.exit(1);
}
if (!token) {
  console.error("Missing SUPABASE_ACCESS_TOKEN");
  process.exit(1);
}

const invokePath = path.join(__dirname, "deploy-payloads", "_mcp", `_invoke-${name}.json`);
const payload = JSON.parse(fs.readFileSync(invokePath, "utf8"));

async function main() {
  const metadata = {
    entrypoint_path: payload.entrypoint_path,
    name: payload.name,
    verify_jwt: payload.verify_jwt,
  };

  const form = new FormData();
  form.append("metadata", JSON.stringify(metadata));

  for (const file of payload.files) {
    const blob = new Blob([file.content], { type: "application/typescript" });
    const fileName = file.name.replace(/^\.\.\/_shared\//, "_shared/");
    form.append("file", blob, fileName);
  }

  const url = `https://api.supabase.com/v1/projects/${PROJECT_REF}/functions/deploy?slug=${encodeURIComponent(name)}`;
  const res = await fetch(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });

  const text = await res.text();
  if (!res.ok) {
    console.error(`FAIL ${name}: ${res.status} ${text.slice(0, 500)}`);
    process.exit(1);
  }
  const result = JSON.parse(text);
  console.log(JSON.stringify({ name, ok: true, version: result.version, id: result.id }));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
