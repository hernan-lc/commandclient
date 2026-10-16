# Native execution and async jobs

## Automatic transport selection

The Fabric client's HTTP URL stays the same when joining a world or server:

1. Integrated singleplayer: local native command source, elevated by default.
2. Multiplayer: authorized, compatible server plugin after negotiation.
3. Multiplayer without an authorized compatible plugin: normal vanilla command
   packets, with the server's existing permissions and limits.

Only slash commands use native transports. Ordinary chat uses the original
packet path. Negotiation waits at most two seconds and resets on disconnect.
Fallback occurs before an EXECUTE message is sent. Once a command may have
reached the server, unknown results never trigger a vanilla retry.

Networking uses generated version-specific packet/codec mixins; Fabric API is
not required. `versions.json` declares payload, identifier, disconnect,
permission and callback capabilities explicitly. `/api/status` exposes active
transport, protocol version, negotiation diagnostic and pending request count.

## Singleplayer

Commands run on the integrated-server thread, including paused-world ticks.
They retain player identity, position, dimension and context. Elevation applies
to the command source only; cheats need not be enabled. Set
`elevatedSingleplayer: false` to use normal player permissions. HTTP mutations
always require bearer authentication. Typed in-game commands are not elevated.

The native queue drains at most `commandsPerTick` entries and stops starting
new entries after two milliseconds of dispatch time. There are no sleep delays.
A single expensive command cannot be interrupted or bounded this way and can
still stall a tick. Connection changes invalidate queued work before dispatch.

## Structured results

The legacy `text`, `success`, `output`, `result` and `results` fields remain.
Each result also has `execution_status`, `code`, `result_callbacks`, and an
optional `return_value`:

| Status | Meaning |
|---|---|
| `accepted` | Validated and queued without waiting on a Minecraft thread |
| `dispatched` | Passed to the command/packet dispatcher; outcome not confirmed |
| `executed` | Success confirmed by Minecraft result callback |
| `failed` | Rejected, exception, or confirmed command failure |
| `unknown` | Completion cannot be established; do not automatically retry |

Numeric result zero can be successful. Missing callbacks are unknown, not
successful execution. Legacy versions use Brigadier callbacks; newer versions
use CommandResultCallback. A successful callback confirms execution, not every
possible delayed world effect. Bukkit/plugin commands expose dispatch results,
not a uniform callback for their ultimate effects.

## Jobs

`POST /api/jobs` accepts the same body as `/api/chat` and returns 202 with a job
ID and `Location: /api/jobs/{id}`. Inspect with GET; cancel with DELETE. Jobs
have `queued`, `running`, `completed`, `failed` or `cancelled` state, `total`,
`processed`, `failed_commands`, `last_error` and partial `results`.

Sync and async submissions share one scheduler. Default concurrency is one,
which preserves global submission order. Increasing `jobConcurrency` allows
jobs to overlap but preserves order within each job. Confirmed failures do not
abort the rest of the batch; an unknown outcome stops remaining dispatch.
Terminal states do not change. Cancellation cannot undo a running command;
partial results may still arrive for that command after cancellation.

`maxJobs` defaults to 128 and `maxQueuedCommands` to 1024. Completed jobs expire
after `retentionSeconds` (600 by default) and can be evicted sooner for capacity.
Full capacity returns 429. Unknown/expired job IDs return 404. Jobs are in-memory
and are cancelled on restart/shutdown. Timeouts cancel queued remaining work
without interrupting Minecraft's dispatcher. The default execution and HTTP
wait timeout is 30 seconds; a synchronous timeout may have an unknown outcome.
Its Location header allows inspecting the corresponding job.

## Installing Paper/Spigot

Build with `./gradlew :server-adapter:build`, or get the distinct server release
artifact. Put `commandapi-server-<version>.jar` in `plugins/` and restart.
The plugin targets Bukkit API 1.21 and Java 21+, packages the shared core, and
uses Gson supplied by Minecraft/Bukkit. It does not package Bukkit, Fabric,
Gson or test dependencies. Folia is unsupported. Compilation is separate from
runtime compatibility evidence.

For connected-client integration, edit `plugins/CommandApiBridge/config.yml`:

```yaml
allowed-commands: [time, weather, seed]
allowed-players: []
commands-per-tick: 8
```

Grant `commandapi.execute` through your server's permission system to selected
players. The grant only permits using the bridge; each command still runs with
that player's native permissions. Default policy denies commands and the
permission defaults to false. Restart the plugin/server to apply configuration.

The optional administrator HTTP API needs explicit setup:

```yaml
http-enabled: true
host: 127.0.0.1
port: 8081
sender: player
player-uuid: 'canonical player UUID'
allowed-commands: [time, weather]
```

A secure token is generated in the plugin config. For console authority, use
`sender: console` **and** `console-enabled: true`. That authority is only for
administrator HTTP calls; client payloads can never request it. A selected
HTTP player must be online. Ephemeral addresses are recorded in
`plugins/CommandApiBridge/commandapi-address.json` and removed on disable.

## Protocol

Channel `commandapi:bridge`, protocol version 1. Binary frames contain magic,
version, kind, request UUID, wall-clock issue time and strict UTF-8 body.
Kinds: HELLO, CAPABILITIES, EXECUTE, EXECUTE_RESULT, JOB_STATUS, CANCEL, ERROR.
Frames are at most 8192 bytes, bodies 4096 bytes; execute text remains capped
at 256 characters. Frames older than 30 seconds or over five seconds into the
future are rejected. Peers therefore need reasonably synchronized clocks.

The server limits each connected player to 16 messages/second and four new
command requests/second, and retains completed request IDs for 60 seconds.
Duplicate requests in this window return the previous outcome without dispatch.
Active records are not evicted and per-session capacity is 512. Client requests
are bounded at 128. No credential or console capability appears in this protocol.
