#!/usr/bin/env node
/** Print deploy args JSON for one function from deploy-payloads/<name>.json */
const fs = require("fs");
const path = require("path");
const name = process.argv[2];
if (!name) { console.error("usage: load-payload.js <name>"); process.exit(1); }
const p = JSON.parse(fs.readFileSync(path.join(__dirname, `${name}.json`), "utf8"));
process.stdout.write(JSON.stringify({
  name: p.name,
  entrypoint_path: p.entrypoint_path,
  verify_jwt: p.verify_jwt,
  files: p.files,
}));
