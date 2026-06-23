#!/usr/bin/env node
/**
 * Regenerate MCP deploy args from live supabase/functions source (includes fresh _shared/security.ts).
 */
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const fnRoot = path.join(root, "supabase", "functions");
const outDir = path.join(__dirname, "deploy-payloads", "_mcp");

const FUNCTIONS = [
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

const securityPath = path.join(fnRoot, "_shared", "security.ts");
const securityContent = fs.readFileSync(securityPath, "utf8");

fs.mkdirSync(outDir, { recursive: true });

for (const fn of FUNCTIONS) {
  const indexPath = path.join(fnRoot, fn.name, "index.ts");
  if (!fs.existsSync(indexPath)) {
    console.error(`Missing ${indexPath}`);
    process.exit(1);
  }
  const args = {
    name: fn.name,
    entrypoint_path: "index.ts",
    verify_jwt: fn.verify_jwt,
    files: [
      { name: "index.ts", content: fs.readFileSync(indexPath, "utf8") },
      { name: "../_shared/security.ts", content: securityContent },
    ],
  };
  const payload = JSON.stringify(args);
  fs.writeFileSync(path.join(outDir, `${fn.name}.args.json`), payload, "utf8");
  fs.writeFileSync(path.join(outDir, `deploy-${fn.name}.json`), payload, "utf8");
  fs.writeFileSync(path.join(outDir, `_invoke-${fn.name}.json`), payload, "utf8");
  console.log(`WROTE ${fn.name} (${args.files[0].content.length + securityContent.length} bytes)`);
}
