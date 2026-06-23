#!/usr/bin/env node
/** Print deploy args JSON for a function name (stdout). */
const fs = require("fs");
const path = require("path");
const name = process.argv[2];
if (!name) {
  console.error("Usage: node load-args.js <function-name>");
  process.exit(1);
}
const file = path.join(__dirname, `${name}.args.json`);
const j = JSON.parse(fs.readFileSync(file, "utf8"));
process.stdout.write(
  JSON.stringify({
    name: j.name,
    entrypoint_path: j.entrypoint_path,
    verify_jwt: j.verify_jwt,
    files: j.files,
  }),
);
