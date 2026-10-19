#!/usr/bin/env node
// CommandClient CLI: functions, jobs and status over the local REST API.
// No dependencies beyond Node 18+ (global fetch).
//
//   commandclient status
//   commandclient functions list [--json]
//   commandclient functions show events:mob_attack [--json]
//   commandclient functions validate ./mob_attack.json [--json]
//   commandclient functions preview events:mob_attack --mob minecraft:zombie --count 5 [--json]
//   commandclient functions run events:mob_attack --mob minecraft:zombie --count 5 \
//       --title "Zombie Invasion!" [--sync|--async] [--json]
//   commandclient function run events:mob_attack ...   (singular alias)
//   commandclient jobs list [--json]
//   commandclient jobs get <job-id> [--json]
//   commandclient jobs cancel <job-id> [--json]
//
// Address discovery mirrors docs/example.js: the port is automatic by
// default, so the address file is read instead of asking for a port.
// Override with COMMANDAPI_URL; tokens come from COMMANDAPI_TOKEN or the
// local config file and are never printed.

const fs = require('fs');
const os = require('os');
const path = require('path');

function configDir() {
  return process.env.COMMANDAPI_CONFIG || path.join(os.homedir(), '.minecraft', 'config');
}

function addressFromFile() {
  try {
    return JSON.parse(
      fs.readFileSync(path.join(configDir(), 'commandapi-address.json'), 'utf8')).url;
  } catch (error) {
    return null;
  }
}

function tokenFromFile() {
  try {
    return JSON.parse(fs.readFileSync(path.join(configDir(), 'commandapi.json'), 'utf8')).token;
  } catch (error) {
    return null;
  }
}

const FILE_URL = addressFromFile();
const BASE_URL = process.env.COMMANDAPI_URL || FILE_URL;
// A URL override never receives another profile's token unless it matches.
const TOKEN = process.env.COMMANDAPI_TOKEN || (BASE_URL === FILE_URL ? tokenFromFile() : null);

function headers() {
  return {
    'Content-Type': 'application/json',
    ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
  };
}

async function request(method, endpoint, body) {
  if (!BASE_URL) {
    throw new Error('No commandapi-address.json found; set COMMANDAPI_CONFIG or COMMANDAPI_URL');
  }
  const options = { method, headers: headers() };
  if (body !== undefined) {
    options.body = JSON.stringify(body);
  }
  const response = await fetch(`${BASE_URL}${endpoint}`, options);
  let data = {};
  try {
    data = await response.json();
  } catch (error) {
    data = { error: `HTTP ${response.status}` };
  }
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

function coerce(value) {
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (/^-?\d+$/.test(value)) return parseInt(value, 10);
  if (/^-?\d*\.\d+$/.test(value)) return parseFloat(value);
  return value;
}

/** Parses trailing --key value / --key=value flags into {args, flags}. */
function parseFlags(argv) {
  const args = {};
  const flags = {};
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (!token.startsWith('--')) continue;
    const eq = token.indexOf('=');
    if (eq >= 0) {
      const key = token.slice(2, eq);
      const value = token.slice(eq + 1);
      if (key === 'arg' && value.includes('=')) {
        const split = value.indexOf('=');
        args[value.slice(0, split)] = coerce(value.slice(split + 1));
      } else if (key === 'json' || key === 'async' || key === 'sync') {
        flags[key] = true;
      } else {
        args[key] = coerce(value);
      }
    } else if (token === '--json' || token === '--async' || token === '--sync') {
      flags[token.slice(2)] = true;
    } else if (token === '--arg' && i + 1 < argv.length && argv[i + 1].includes('=')) {
      const pair = argv[++i];
      const split = pair.indexOf('=');
      args[pair.slice(0, split)] = coerce(pair.slice(split + 1));
    } else if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) {
      args[token.slice(2)] = coerce(argv[++i]);
    } else {
      flags[token.slice(2)] = true;
    }
  }
  return { args, flags };
}

function output(data, asJson) {
  if (asJson) {
    console.log(JSON.stringify(data));
  } else {
    console.log(JSON.stringify(data, null, 2));
  }
}

function usage() {
  console.error(`Usage:
  commandclient status
  commandclient functions list [--json]
  commandclient functions show <id> [--json]
  commandclient functions validate <file> [--json]
  commandclient functions preview <id> [--name value ...] [--json]
  commandclient functions run <id> [--name value ...] [--async|--sync] [--json]
  commandclient jobs list [--json]
  commandclient jobs get <job-id> [--json]
  commandclient jobs cancel <job-id> [--json]`);
}

async function main() {
  const [group, noun, ...rest] = process.argv.slice(2);
  if (!group || group === 'help' || group === '--help') {
    usage();
    return;
  }
  if (group === 'status') {
    output(await request('GET', '/api/status'), rest.includes('--json'));
    return;
  }
  if (group === 'jobs') {
    const asJson = rest.includes('--json');
    if (noun === 'list' || noun === undefined) {
      output(await request('GET', '/api/status').then((s) => ({
        queue_depth: s.queue_depth,
        queued_commands: s.queued_commands,
        active_jobs: s.active_jobs,
        max_jobs: s.max_jobs,
      })), asJson);
      return;
    }
    if (noun === 'get' && rest[0]) {
      output(await request('GET', `/api/jobs/${encodeURIComponent(rest[0])}`), asJson);
      return;
    }
    if (noun === 'cancel' && rest[0]) {
      output(await request('DELETE', `/api/jobs/${encodeURIComponent(rest[0])}`), asJson);
      return;
    }
    usage();
    process.exitCode = 1;
    return;
  }
  if (group === 'functions' || group === 'function') {
    if (group === 'function' && noun !== 'run') {
      console.error('Did you mean "functions"? Only "function run" is supported as an alias.');
      process.exitCode = 1;
      return;
    }
    const positional = rest.filter((t) => !t.startsWith('--'));
    const { args, flags } = parseFlags(rest);
    const asJson = !!flags.json;
    if (noun === 'list') {
      const data = await request('GET', '/api/functions');
      if (asJson) {
        output(data, true);
      } else if (data.functions.length === 0) {
        console.log('No functions registered.');
      } else {
        for (const fn of data.functions) {
          console.log(`${fn.id} - ${fn.description || fn.name} [${fn.format}]`);
        }
      }
      return;
    }
    if (noun === 'show' && positional[0]) {
      output(await request('GET', `/api/functions/${encodeURIComponent(positional[0])}`), asJson);
      return;
    }
    if (noun === 'validate' && positional[0]) {
      const file = positional[0];
      const source = fs.readFileSync(file, 'utf8');
      const format = file.endsWith('.mcfunction') ? 'mcfunction' : 'json';
      output(await request('POST', '/api/functions/validate', {
        id: `validate:${path.basename(file, path.extname(file))}`,
        format,
        source,
      }), asJson);
      return;
    }
    if ((noun === 'preview' || noun === 'run') && positional[0]) {
      const id = positional[0];
      if (noun === 'preview') {
        output(await request('POST', `/api/functions/${encodeURIComponent(id)}/preview`, { args }), asJson);
        return;
      }
      const asyncMode = flags.async || (!flags.sync && true);
      const data = await request('POST', `/api/functions/${encodeURIComponent(id)}/run`, {
        args,
        async: asyncMode,
      });
      output(data, asJson);
      return;
    }
    usage();
    process.exitCode = 1;
    return;
  }
  usage();
  process.exitCode = 1;
}

main().catch((error) => {
  console.error('Error:', error.message);
  process.exitCode = 1;
});
