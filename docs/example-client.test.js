const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

test('example reads profile token only for its recorded URL', async () => {
  const config = fs.mkdtempSync(path.join(os.tmpdir(), 'commandapi-example-'));
  const headers = [];
  const server = http.createServer((req, res) => {
    headers.push(req.headers.authorization);
    res.setHeader('Content-Type', 'application/json');
    if (req.headers.authorization !== 'Bearer test-secret') {
      res.writeHead(401); res.end('{"error":"Unauthorized"}'); return;
    }
    if (req.url === '/api/status') res.end('{"in_world":true,"minecraft_version":"test"}');
    else res.end('{"success":true,"result":{"text":"test","success":true,"output":"sent"}}');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  fs.writeFileSync(path.join(config, 'commandapi-address.json'), JSON.stringify({ url }));
  fs.writeFileSync(path.join(config, 'commandapi.json'), JSON.stringify({ token: 'test-secret' }));
  const run = extra => new Promise(resolve => {
    const child = spawn(process.execPath, [path.join(__dirname, 'example.js'), 'test'], {
      env: { ...process.env, COMMANDAPI_CONFIG: config, COMMANDAPI_TOKEN: '', COMMANDAPI_URL: '', ...extra }, stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = ''; child.stdout.on('data', d => output += d); child.stderr.on('data', d => output += d);
    child.on('close', code => resolve({ code, output }));
  });
  try {
    const matching = await run({}); assert.equal(matching.code, 0); assert.deepEqual(headers, ['Bearer test-secret', 'Bearer test-secret']);
    assert.ok(!matching.output.includes('test-secret'));
    const override = await run({ COMMANDAPI_URL: url + '/' }); assert.equal(override.code, 1);
    assert.equal(headers.at(-1), undefined); assert.ok(!override.output.includes('test-secret'));
  } finally { await new Promise(resolve => server.close(resolve)); fs.rmSync(config, { recursive: true }); }
});
