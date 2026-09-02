# API reference

Command API is a **client-side** mod. The HTTP server runs inside your
Minecraft client and acts as your player: it sends chat messages and commands
exactly as if you had typed them. It cannot administrate a dedicated server,
read the player list or stop a server.

Base URL: `http://127.0.0.1:<port>` — the port is automatic by default
(`"port": 0` in `config/commandapi.json`), so read it from the game log or
from `config/commandapi-address.json` (rewritten on every start with the bound
`host`, `port` and `url`). The examples below use port `8080` as a placeholder;
substitute your bound port. To pin a fixed port, set `"port"` explicitly or run
`/commandapi port <n>` in game.

## Authentication

Off by default. Turn it on in `config/commandapi.json`:

```json
{
  "token": "a-long-random-string",
  "authEnabled": true
}
```

Then send the token on every request:

```
Authorization: Bearer a-long-random-string
```

Requests without a valid token get `401`. Authentication is skipped when
`authEnabled` is `false` or the token is empty — which is why the server binds
loopback by default. Tokens are never written to the log.

## `GET /api/status`

```bash
curl http://127.0.0.1:8080/api/status
```

```json
{
  "status": "running",
  "mode": "client-chat",
  "mod_version": "1.1.0+mc1.16.1",
  "minecraft_version": "1.16.1",
  "host": "127.0.0.1",
  "port": 8080,
  "url": "http://127.0.0.1:8080",
  "auth_enabled": false,
  "in_world": true,
  "player_name": "Steve",
  "endpoints": {
    "/api/status": "GET - Check API status",
    "/api/chat": "POST - Send chat message",
    "/api/execute": "POST - Alias for /api/chat"
  }
}
```

`player_name` is present only when `in_world` is `true`.

## `POST /api/chat`

Sends one or more messages as the local player. A message starting with `/` is
sent as a command.

### One message

```bash
curl -X POST http://127.0.0.1:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"text": "hello world"}'
```

```json
{
  "result": { "text": "hello world", "success": true, "output": "Message sent to chat" },
  "success": true
}
```

`{"command": "..."}` is accepted as an alias for `{"text": "..."}`.

### Several messages

```bash
curl -X POST http://127.0.0.1:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"messages": ["hello", "/time set day"]}'
```

```json
{
  "results": [
    { "text": "hello", "success": true, "output": "Message sent to chat" },
    { "text": "/time set day", "success": true, "output": "Command sent" }
  ],
  "success": true
}
```

Messages are sent in order. A failure of one entry does not abort the rest: the
HTTP status stays `200` and the failing entry carries `"success": false`.

### When you are not in a world

The request is answered with **HTTP 503** and nothing is sent. The body keeps
the normal shape so existing clients can still read `result` / `results`:

```json
{
  "result": { "text": "hello", "success": false, "output": "Player not available (not in world?)" },
  "success": false,
  "error": "Player not available (not in world?)"
}
```

Earlier versions returned `200` here. Check the status code if you care about
the difference between "sent" and "could not send".

## `POST /api/execute`

Alias of `/api/chat`, kept so existing clients keep working. Same request and
response format.

## In-game commands

Type `/commandapi ...` in chat to inspect and change the config without leaving
the game. These lines are intercepted client-side: they are answered locally
and never sent to the server. Every change is written to `commandapi.json` and
the server restarts on it at once.

| Command | Effect |
|---|---|
| `/commandapi` or `/commandapi help` | Usage |
| `/commandapi status` | Bound address, effective config, running state |
| `/commandapi port <0-65535>` | `0` picks a free port; a taken port keeps the old config |
| `/commandapi host <address>` | Change the bind address (warns when exposed without auth) |
| `/commandapi auth <on\|off>` | Turning on requires a token first |
| `/commandapi token <secret\|clear>` | Setting a token enables auth; `clear` disables auth too |
| `/commandapi reload` | Re-read `commandapi.json` from disk and restart |
| `/commandapi restart` | Restart on the current config |

## Limits

| Limit | Value | Exceeded gives |
|---|---|---|
| Request body | 64 KiB | `413` |
| Messages per batch | 32 | `400` |
| Message length | 256 characters (Minecraft's own chat limit) | `400` |

## Errors

| Status | When |
|---|---|
| `400` | body is not a JSON object, has no usable `text` / `messages` field, or breaks a limit |
| `401` | authentication enabled and the Bearer token is missing or wrong |
| `404` | unknown endpoint |
| `405` | wrong method (`/api/chat` is POST only, `/api/status` GET only); the response carries an `Allow` header |
| `413` | request body over 64 KiB |
| `503` | no player available — you are not in a world, so nothing was sent |
| `500` | unexpected failure while handling the request |

No request can kill a worker thread: every handler answers, even on an
unexpected error.

```json
{ "error": "Missing 'text' or 'messages' field", "status": 400 }
```

## Threading

HTTP requests arrive on worker threads, but Minecraft may only be touched from
the client thread. Every send is scheduled onto the client thread and the
request waits up to 5 seconds for it; on timeout the entry reports
`"Timed out waiting for the Minecraft client thread"`. This is handled once, in
`ClientThreadBridge`, for all versions.

## Example client

```js
const BASE_URL = 'http://127.0.0.1:8080';
const TOKEN = null; // set when authEnabled is true

async function send(text) {
  const response = await fetch(`${BASE_URL}/api/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
    },
    body: JSON.stringify({ text }),
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

send('/seed').then(console.log).catch(console.error);
```

See [example.js](example.js) for a runnable version.
