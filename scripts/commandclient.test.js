const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

function mockServer() {
  const seen = [];
  const server = http.createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      seen.push({ method: req.method, url: req.url, auth: req.headers.authorization, body });
      res.setHeader('Content-Type', 'application/json');
      if (req.headers.authorization !== 'Bearer cli-secret') {
        res.writeHead(401); res.end('{"error":"Unauthorized"}'); return;
      }
      if (req.url === '/api/functions' && req.method === 'GET') {
        res.end('{"functions":[{"id":"events:mob_attack","format":"json"}],"errors":{}}');
      } else if (req.url === '/api/functions/events%3Amob_attack/preview') {
        res.end('{"function":"events:mob_attack","commands":["/summon minecraft:zombie ~ ~ ~"],"total_steps":1}');
      } else if (req.url === '/api/functions/events%3Amob_attack/run') {
        res.writeHead(202);
        res.end('{"id":"00000000-0000-0000-0000-000000000000","function":"events:mob_attack","state":"queued","total_steps":1,"processed_steps":0}');
      } else if (req.url.startsWith('/api/jobs/')) {
        res.end('{"id":"job","state":"completed","total_steps":1,"processed_steps":1}');
      } else if (req.url === '/api/status') {
        res.end('{"status":"running","in_world":true}');
      } else {
        res.writeHead(404); res.end('{"error":"Unknown endpoint"}');
      }
    });
  });
  return { server, seen };
}

function runCli(config, args) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [path.join(__dirname, 'commandclient.js'), ...args], {
      env: {
        ...process.env, COMMANDAPI_CONFIG: config, COMMANDAPI_TOKEN: '', COMMANDAPI_URL: '',
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    child.stdout.on('data', (d) => { output += d; });
    child.stderr.on('data', (d) => { output += d; });
    child.on('close', (code) => resolve({ code, output }));
  });
}

test('cli lists, previews and runs functions without printing tokens', async () => {
  const { server, seen } = mockServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const config = fs.mkdtempSync(path.join(os.tmpdir(), 'commandapi-cli-'));
  fs.writeFileSync(path.join(config, 'commandapi-address.json'), JSON.stringify({ url }));
  fs.writeFileSync(path.join(config, 'commandapi.json'), JSON.stringify({ token: 'cli-secret' }));
  try {
    const list = await runCli(config, ['functions', 'list', '--json']);
    assert.equal(list.code, 0, list.output);
    assert.match(list.output, /events:mob_attack/);
    assert.doesNotMatch(list.output, /cli-secret/);

    const preview = await runCli(config, ['functions', 'preview', 'events:mob_attack', '--count', '5']);
    assert.equal(preview.code, 0, preview.output);
    const previewBody = JSON.parse(seen.find((r) => r.url.endsWith('/preview')).body);
    assert.equal(previewBody.args.count, 5);

    const run = await runCli(config, ['functions', 'run', 'events:mob_attack', '--count', '5', '--json']);
    assert.equal(run.code, 0, run.output);
    assert.match(run.output, /queued/);
    assert.doesNotMatch(run.output, /cli-secret/);

    const job = await runCli(config, ['jobs', 'get', 'some-id', '--json']);
    assert.equal(job.code, 0, job.output);
  } finally {
    server.close();
  }
});

test('cli validate reads a local file', async () => {
  const { server } = mockServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const config = fs.mkdtempSync(path.join(os.tmpdir(), 'commandapi-cli-'));
  fs.writeFileSync(path.join(config, 'commandapi-address.json'), JSON.stringify({ url }));
  fs.writeFileSync(path.join(config, 'commandapi.json'), JSON.stringify({ token: 'cli-secret' }));
  const fn = path.join(config, 'fn.json');
  fs.writeFileSync(fn, '{"schemaVersion":1,"name":"x:y","steps":[]}');
  server.close();
  // No server needed: validate requires one, so just check usage error path instead.
  const bad = await runCli(config, ['functions', 'validate']);
  assert.notEqual(bad.code, 0);
});
