const fs = require('fs');
const path = require('path');
const name = process.argv[2];
if (!name) {
  console.error('Usage: node read-deploy-payload.js <function-name>');
  process.exit(1);
}
const file = path.join(__dirname, 'deploy-payloads', '_mcp', '_live', `${name}.json`);
const payload = JSON.parse(fs.readFileSync(file, 'utf8'));
process.stdout.write(JSON.stringify(payload));
