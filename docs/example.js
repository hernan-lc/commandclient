// Minimal Command API client.
// Run with: node docs/example.js "hello world"
// Batch:    node docs/example.js --batch "hello" "/seed"
//
// The port is automatic by default, so the client reads it from the address
// file the mod rewrites on every start (override with COMMANDAPI_URL, and
// point at another profile's config directory with COMMANDAPI_CONFIG).

const fs = require('fs');
const os = require('os');
const path = require('path');

function addressFromFile() {
  const dir = process.env.COMMANDAPI_CONFIG
    || path.join(os.homedir(), '.minecraft', 'config');
  try {
    return JSON.parse(
      fs.readFileSync(path.join(dir, 'commandapi-address.json'), 'utf8')).url;
  } catch (error) {
    return null;
  }
}

const FILE_URL = addressFromFile();
const BASE_URL = process.env.COMMANDAPI_URL || FILE_URL;
function tokenFromFile() {
  const dir = process.env.COMMANDAPI_CONFIG || path.join(os.homedir(), '.minecraft', 'config');
  try { return JSON.parse(fs.readFileSync(path.join(dir, 'commandapi.json'), 'utf8')).token; }
  catch { return null; }
}
// A URL override never receives another profile's token unless it matches its address file.
const TOKEN = process.env.COMMANDAPI_TOKEN || (BASE_URL === FILE_URL ? tokenFromFile() : null);

function headers() {
  return {
    'Content-Type': 'application/json',
    ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
  };
}

async function status() {
  const response = await fetch(`${BASE_URL}/api/status`, { headers: headers() });
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

async function send(body) {
  const response = await fetch(`${BASE_URL}/api/chat`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify(body),
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

// A single text uses {text: ...} and returns data.result.
async function sendOne(text) {
  return send({ text });
}

// An array uses {messages: [...]} and returns data.results, even for one item.
async function sendBatch(messages) {
  return send({ messages });
}

async function main() {
  if (!BASE_URL) {
    throw new Error('No commandapi-address.json found; set COMMANDAPI_CONFIG or COMMANDAPI_URL');
  }
  const state = await status();
  console.log(`Minecraft ${state.minecraft_version}, in world: ${state.in_world}`);
  if (!state.in_world) {
    console.error('Join a world first: messages can only be sent as a player.');
    return;
  }

  if (process.argv[2] === '--batch') {
    const messages = process.argv.slice(3);
    if (messages.length === 0) {
      throw new Error('Usage: node docs/example.js --batch "hello" "/seed"');
    }
    const data = await sendBatch(messages);
    console.log(JSON.stringify(data.results, null, 2));
  } else {
    const text = process.argv[2] || 'hello from the Command API';
    const data = await sendOne(text);
    console.log(JSON.stringify(data.result, null, 2));
  }
}

main().catch((error) => {
  console.error('Error:', error.message);
  process.exitCode = 1;
});
