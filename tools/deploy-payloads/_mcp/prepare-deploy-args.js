#!/usr/bin/env node
/** Prepare exact deploy args for one function. Usage: node prepare-deploy-args.js <name> */
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const name = process.argv[2];
if (!name) {
  console.error("Usage: node prepare-deploy-args.js <function-name>");
  process.exit(1);
}
const out = execFileSync(
  process.execPath,
  [path.join(__dirname, "run-deploy-one.js"), name],
  { encoding: "utf8" },
);
const args = JSON.parse(out);
const target = path.join(__dirname, "_last-deploy-args.json");
fs.writeFileSync(target, out);
console.log(JSON.stringify({ name, verify_jwt: args.verify_jwt, secLen: args.files[1].content.length, file: target }));
