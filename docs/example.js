// Minimal Command API client.
// Run with: node docs/example.js "hello world"
//
// The port is automatic by default, so the client reads it from the address
// file the mod rewrites on every start (override with COMMANDAPI_URL, and
// point at another launcher profile with COMMANDAPI_CONFIG).

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

const BASE_URL = process.env.COMMANDAPI_URL || addressFromFile() || 'http://127.0.0.1:8080';
const TOKEN = process.env.COMMANDAPI_TOKEN || null; // needed only when authEnabled is true

function headers() {
  return {
    'Content-Type': 'application/json',
    ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
  };
}

async function status() {
  const response = await fetch(`${BASE_URL}/api/status`, { headers: headers() });
  return response.json();
}

async function send(text) {
  const response = await fetch(`${BASE_URL}/api/chat`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ text }),
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

async function main() {
  const state = await status();
  console.log(`Minecraft ${state.minecraft_version}, in world: ${state.in_world}`);
  if (!state.in_world) {
    console.error('Join a world first: messages can only be sent as a player.');
    return;
  }

  const text = process.argv[2] || 'hello from the Command API';
  console.log(await send(text));
}

main().catch((error) => {
  console.error('Error:', error.message);
  process.exitCode = 1;
});
