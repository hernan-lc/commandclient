# API reference

Command API is a **client-side** mod. The HTTP server runs inside your
Minecraft client and acts as your player: it sends chat messages and commands
exactly as if you had typed them. It cannot administrate a dedicated server,
read the player list or stop a server.

Base URL (default): `http://127.0.0.1:8080`

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

```json
{
  "result": { "text": "hello", "success": false, "output": "Player not available (not in world?)" },
  "success": true
}
```

## `POST /api/execute`

Alias of `/api/chat`, kept so existing clients keep working. Same request and
response format.

## Errors

| Status | When |
|---|---|
| `400` | body is not a JSON object, or has no usable `text` / `messages` field |
| `401` | authentication enabled and the Bearer token is missing or wrong |
| `405` | `/api/chat` or `/api/execute` called with a method other than POST |
| `500` | unexpected failure while handling the request |

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
