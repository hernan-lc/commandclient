# API reference

Command API is a **client-side** mod. The HTTP server runs inside your
Minecraft client and acts as your player: it submits chat messages and commands
through the same client methods used for typed input. A successful HTTP response
does not prove a server accepted or executed a command. It cannot administrate
a dedicated server, read the player list or stop a server.

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
`authEnabled` is `false` or the token is empty. The default host is loopback;
set a nonempty token and enable authentication before exposing the bind address.
Tokens are never written to the log.

## `GET /api/status`

```bash
curl http://127.0.0.1:8080/api/status
```

```json
{
  "status": "running",
  "mode": "client-chat",
  "mod_version": "1.3.2+mc1.16.1",
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

`player_name` is present only when `in_world` is `true`. A player can briefly
exist without a usable server connection during transitions; a send in that
interval can return an entry with `"success": false`.

## `POST /api/chat`

Sends one or more messages as the local player. A message starting with `/` is
submitted as a command. Join a world before sending. The JSON body must be an
object containing one of these fields:

| Field | Type | Result |
|---|---|---|
| `text` | string | One message; response contains `result` |
| `command` | string | Alias for `text`; include the leading `/` to send a command |
| `messages` | array of strings | One to 32 messages in order; response contains `results` |

An empty string, empty `messages` array, or non-string entry returns `400`.
If more than one field is present, the parser uses `text`, then `command`, then
`messages`. Use one field per request to avoid ambiguity.

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

The legacy form `{"command": "/seed"}` has the same response shape. The
`command` field does not add a slash for you.

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
    { "text": "/time set day", "success": true, "output": "Command submitted" }
  ],
  "success": true
}
```

Messages are sent in order. A failure of one entry does not abort the rest: the
HTTP status stays `200` and the failing entry carries `"success": false`.
The outer `"success": true` means the request was handled; inspect every
entry to see which submissions succeeded. Even an entry with `"success": true`
only confirms submission to the Minecraft client. For example, a server can
reject `/time set day` when the player lacks permission.

A one-element `messages` array is still a batch and returns `results`, not
`result`. For a batch request while out of world, HTTP `503` returns the same
`results` array with a failed entry for each input.

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

Earlier versions returned `200` here. Check the status code to distinguish an
offline client from a request handled while in world.

## `POST /api/execute`

Alias of `/api/chat`, kept so existing clients keep working. Same request and
response format. Both single and `messages` array bodies are accepted:

```bash
curl -X POST http://127.0.0.1:8080/api/execute \
  -H 'Content-Type: application/json' \
  -d '{"messages": ["hello", "/seed"]}'
```

## In-game commands

Type `/commandapi ...` in chat to inspect and change the config without leaving
the game. These lines are intercepted client-side: they are answered locally
and never sent to the server. Successful changes are written to
`commandapi.json` and applied at once: bind settings (`port`, `host`, `auth`, `token`) restart the
server on the new config, while `login` applies without a restart.

When you join a world, the `status` summary (bound address, port mode, auth
and token state) is printed in chat automatically. Turn it off with
`/commandapi login off`.

| Command | Effect |
|---|---|
| `/commandapi` or `/commandapi help` | Usage |
| `/commandapi status` | Bound address, effective config, running state |
| `/commandapi port <0-65535>` | `0` picks a free port; a taken port keeps the old config |
| `/commandapi host <address>` | Change the bind address (warns when exposed without auth) |
| `/commandapi auth <on\|off>` | Turning on requires a token first |
| `/commandapi token <secret\|clear>` | Setting a token enables auth; `clear` disables auth too |
| `/commandapi login <on\|off>` | World-join summary on/off (default on; no restart) |
| `/commandapi reload` | Re-read `commandapi.json` from disk and restart |
| `/commandapi restart` | Restart on the current config |

## Limits

| Limit | Value | Exceeded gives |
|---|---|---|
| Request body | 64 KiB | `413` |
| Messages per batch | 32 | `400` |
| Message length | 256 Java UTF-16 code units per entry | `400` |

## Errors

| Status | When |
|---|---|
| `400` | body is not a JSON object, has no usable `text`, `command`, or `messages` field, or breaks a limit |
| `401` | authentication enabled and the Bearer token is missing or wrong |
| `404` | unknown endpoint |
| `405` | wrong method (`/api/chat` and `/api/execute` are POST only, `/api/status` GET only); the response carries an `Allow` header |
| `413` | request body over 64 KiB |
| `503` | no player available — you are not in a world, so nothing was sent |
| `500` | unexpected failure while handling the request |

Handlers convert unexpected failures to JSON `500` when the connection is
still open. A client that disconnects before the response cannot receive it.

```json
{ "error": "Missing 'text' or 'messages' field", "status": 400 }
```

## Threading

HTTP requests arrive on worker threads, but Minecraft state is read and used on
the client thread. Each operation is scheduled there and the request waits up
to 5 seconds; on timeout a send entry reports
`"Timed out waiting for the Minecraft client thread"`. This is handled once, in
`ClientThreadBridge`, for all versions.

## Example client

```js
const BASE_URL = 'http://127.0.0.1:8080';
const TOKEN = null; // set when authEnabled is true

async function send(body) {
  const response = await fetch(`${BASE_URL}/api/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
    },
    body: JSON.stringify(body),
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

async function main() {
  console.log((await send({ text: '/seed' })).result);
  console.log((await send({ messages: ['hello', '/seed'] })).results);
}

main().catch(console.error);
```

The first call returns `result`; the second returns `results`. This example
uses Node.js 18 or newer, where `fetch` is built in. See [example.js](example.js)
for a runnable client with single and batch modes.

## Native execution and asynchronous jobs

See [native execution](NATIVE_EXECUTION.md) for elevated singleplayer commands,
`POST /api/jobs`, `GET/DELETE /api/jobs/{id}`, and the Paper/Spigot adapter.
