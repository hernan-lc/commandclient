# Native commands and asynchronous jobs

## Singleplayer

Slash commands submitted through the API execute directly on the integrated
server thread. The command source keeps the local player's location, entity
and dimension, with elevated permissions (level 4 on older versions, all
permissions on newer versions). Cheats need not be enabled. This elevation
applies to the command source only; it does not permanently make the player
an operator or change the world's settings. Commands do not travel through
chat/command packets and there are no artificial intervals.

Ordinary chat still uses Minecraft's normal connection. Remote servers without
the server plugin still receive normal command packets and enforce their own
spam limits. Commands typed in the game's chat are not elevated by this feature.
The existing loopback API can now execute privileged commands in a local world;
its token therefore grants that authority when authentication is enabled.

## Async API

`POST /api/jobs` accepts the same `text`, `command`, or `messages` body as
`/api/chat`. It returns **202** with `id`, `state`, `total`, `processed`, and
`results`, and a `Location: /api/jobs/{id}` header.

```sh
curl -X POST "$URL/api/jobs" -H 'Content-Type: application/json' \
  -d '{"messages":["/time set day","/weather clear"]}'
curl "$URL/api/jobs/JOB_ID"
curl -X DELETE "$URL/api/jobs/JOB_ID"
```

All routes use the configured bearer authentication. Jobs execute in submission
order on one worker, without sleeps or rate limits. States are `queued`,
`running`, `completed`, `failed`, and `cancelled`. A failed command does not
prevent the remaining commands in the batch from being attempted. Native
Minecraft processing can report command errors through in-game feedback;
`completed` alone does not establish that every requested world effect occurred.

There are at most 128 retained jobs, each with the existing 32-message batch
limit. The oldest finished job is evicted when space is needed; if all retained
jobs are active, submission returns **429**. Unknown or evicted identifiers
return **404**. Jobs are in memory and disappear on restart. Changing connection
cancels outstanding commands before they can be sent to another session.

Native execution has a 30-second timeout; a timed-out command already running
may still finish. Cancellation and shutdown prevent remaining commands. They cannot undo or stop
a command that is already running. One expensive command can still stall the
server tick. HTTP submission is asynchronous; world changes must execute on the
Minecraft/Bukkit server thread.

`/api/chat` and `/api/execute` retain their synchronous response format.

## Paper / Spigot plugin

Build with `./gradlew :server-adapter:build`. Install
`server-adapter/build/libs/commandapi-server-<version>.jar` into `plugins/`
and restart the server. The plugin targets the Bukkit/Spigot 1.21 API; Folia
is not supported. Runtime compatibility on individual server versions must be
verified in game.

The plugin starts its own HTTP API using the same routes, including `/api/jobs`.
It is a direct server adapter: callers use the plugin's HTTP URL rather than
the Fabric client's URL. No custom payload support or client-plugin handshake
is required, and the Fabric client does not automatically forward to it.

Edit `plugins/CommandApiBridge/config.yml`:

```yaml
host: 127.0.0.1
port: 8081
token: 'generated automatically on first launch'
sender: player
player-uuid: 'UUID of the player to control'
```

The selected player must be online. Execution keeps their permissions and
location. Alternatively, explicitly set `sender: console` for server console
authority; commands using player-relative selectors or coordinates should use
`/execute as ... at ... run ...` as appropriate. The plugin never temporarily
ops a player. Authentication is mandatory and the token is generated and saved
on first launch. Set a fixed port when callers need a stable address; otherwise
the chosen ephemeral port is printed in the server log and recorded in
`plugins/CommandApiBridge/commandapi-address.json`.

Only slash commands are accepted by the server adapter. They are dispatched
with Bukkit on the main server thread, avoiding incoming chat command spam
counters. Other plugins may still impose command restrictions, permissions,
or their own limits. A successful dispatch is not proof of the command's
ultimate effect. The API does not currently capture plugin command output.
