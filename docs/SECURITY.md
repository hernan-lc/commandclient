# Security

HTTP commands can control the local player and, in singleplayer, create a
command source with elevated permissions. Treat the API token as authority
over that world. Ordinary commands typed in Minecraft chat are unaffected.

## Authentication and migration

Fresh configurations bind to `127.0.0.1`, use an ephemeral port, enable
authentication, and generate a 256-bit SecureRandom bearer token. The token is
saved in `config/commandapi.json`; it never appears in status, address discovery,
protocol capabilities or audit logs. POSIX configuration files are owner-only
(`0600`), including temporary files used for atomic writes.

An old configuration with no token is migrated to a generated token and
`authEnabled: true`. A missing token field is generated on first launch.
Explicit `authEnabled: true` with `token: ""` is rejected. Invalid configuration
files stop startup rather than replacing restrictive policies with defaults.
Read the generated token locally; update your caller to send
`Authorization: Bearer <token>`. The bundled Node client can read both address
and token from the chosen profile directory.

`authEnabled: false` only permits unauthenticated reads. **Every POST and DELETE
still requires a valid configured token**, including chat and native execution.
Clearing a token blocks these operations until a new token is configured.

## HTTP boundary

Requests carrying an `Origin` header or `Sec-Fetch-Site: cross-site` are rejected,
including requests from localhost browser applications. There is no CORS
allowlist or unauthenticated browser integration. Mutation clients must send
`Content-Type: application/json`; decoding requires valid UTF-8. Only exact
known paths and methods are supported. Bodies are bounded at 64 KiB, batches at
32 messages, and text at 256 UTF-16 code units. Worker and command queues are
bounded; command capacity exhaustion returns 429.

Binding beyond loopback requires **both** authentication and an explicit
`allowRemotePrivileged: true`. Changing `host` alone is rejected. HTTP is plain
HTTP: use a trusted encrypted tunnel or TLS reverse proxy when transmitting
credentials beyond the local machine.

## Command policies

`allowedCommands` selects exact command roots, including namespace prefixes.
`[]` denies slash commands; `["time", "weather"]` permits those roots;
`["*"]` explicitly permits every root. Namespaces are distinct: allowing `time`
does not allow `minecraft:time`. Allowing delegation roots such as `execute`,
`function` or plugin aliases delegates the authority reachable through them;
this is a root allowlist, not a parser for arbitrary nested command languages.
Chat remains on the normal connection path.

`elevatedSingleplayer: true` applies elevation to the local command source only.
Setting it to false preserves the player's normal native permissions. It never
changes operator status or world settings. A remote server is never elevated
by the client. Timed-out or unknown executions must not be automatically retried.

## Server plugin

The independent administrator HTTP API is off by default. Enabling player HTTP
mode requires a selected player UUID. Console HTTP mode additionally requires
`sender: console` and `console-enabled: true`. Its separate bearer token remains
in the administrator's plugin configuration and is never sent to clients.

Plugin-message execution always uses the player supplied by the established
server connection. It requires `commandapi.execute` (default false), the command
root policy (default empty), and any configured UUID allowlist. Payload fields
cannot select another player or request console execution. Dispatcher calls
run on the Bukkit main thread, with native permissions, after command events;
cancellation by another plugin prevents dispatch. Event rewrites are checked
against the root policy again. The bridge does not disable third-party spam,
permission or cancellation policies.

Messages have strict versioned decoding, size and time limits, correlated UUID
request IDs, per-player ingress/command limits, a bounded replay cache covering
the frame validity window, and cancellation on disconnect. Tokens are absent
from the protocol. Audit logs contain only sender UUID and result code, never
command text or credentials. Folia is unsupported.

## Reporting

Report issues at https://github.com/nglmercer/commandclient/issues without tokens
or unredacted configuration files. See [runtime evidence](RUNTIME_VERIFICATION.md)
for the limits of the verification performed.
