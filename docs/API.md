# HTTP API

Fabric exposes an authenticated API inside the Minecraft client. It selects
singleplayer native, authorized server plugin, or vanilla command transport
automatically. The optional plugin administrator HTTP API exposes the same
routes independently. See [native execution](NATIVE_EXECUTION.md).

Read `config/commandapi-address.json` for the actual URL and
`config/commandapi.json` for the generated token. The plugin uses equivalent
files under `plugins/CommandApiBridge/`. Never copy the token into logs or
status reports. `docs/example.js` reads the selected local profile safely.

Every mutation requires `Authorization: Bearer <token>`. Reads also require it
by default. `authEnabled: false` only opens read-only routes. POST bodies must
have `Content-Type: application/json`. Browser-origin requests are rejected.

| Route | Method | Behavior |
|---|---|---|
| `/api/status` | GET | Player, transport, capabilities and queue status |
| `/api/chat` | POST | Wait for an ordered submission/result |
| `/api/execute` | POST | Compatible alias of `/api/chat` |
| `/api/jobs` | POST | Queue work; return 202 and Location |
| `/api/jobs/{id}` | GET | Inspect state and partial results |
| `/api/jobs/{id}` | DELETE | Cancel remaining work |
| `/api/functions` | GET | List functions |
| `/api/functions/{id}` | GET | Inspect function |
| `/api/functions/{id}/run` | POST | Execute function (`{"args":{...},"async":true}`) |
| `/api/functions/{id}/preview` | POST | Preview resolved commands without executing |
| `/api/functions/validate` | POST | Validate a submitted function |
| `/api/functions/reload` | POST | Reload functions from disk |
| `/api/functions` | POST | Create function (auth required) |
| `/api/functions/{id}` | PUT/DELETE | Update/delete function (auth required) |

Function details, preview/run shapes and management auth: [FUNCTION_API.md](FUNCTION_API.md).

Paths are exact. Requests use `{"text":"/time set day"}`, the legacy
`{"command":"/seed"}`, or `{"messages":["/time set day","/weather clear"]}`.
When several fields appear, precedence remains text, command, messages. Batch
size is 1–32, text length 1–256 UTF-16 code units, body size at most 64 KiB.

## Example

```bash
# TOKEN is read locally from the selected profile; do not print it.
curl "$URL/api/status" -H "Authorization: Bearer $TOKEN"
curl -X POST "$URL/api/jobs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"messages":["/time set day","/weather clear"]}'
curl "$URL/api/jobs/JOB_ID" -H "Authorization: Bearer $TOKEN"
curl -X DELETE "$URL/api/jobs/JOB_ID" -H "Authorization: Bearer $TOKEN"
```

## Results

Single submissions keep `result`; batches keep `results`, including one-item
batches. Both keep outer `success`, which is false if any result is unsuccessful.
Each result keeps `text`, `success` and `output`, and adds `execution_status`
(accepted, dispatched, executed, failed, unknown), structured `code`,
`result_callbacks`, and optional `return_value`. Success on vanilla/plugin
transport confirms dispatch, not successful world effects. A confirmed native
callback has execution_status executed. Unknown outcomes must not be retried
automatically. See [result semantics](NATIVE_EXECUTION.md#structured-results).

Confirmed failures do not abort subsequent entries. Unknown execution stops
remaining dispatch. Synchronous calls share the same scheduler as jobs and
return a Location header for their work. A synchronous wait timeout returns
unknown entries for outcomes it cannot establish and cancels remaining work.
It cannot stop a command that is already executing.

Jobs expose ID, state, total, processed, failed_commands, last_error and partial
results. State is one of queued, running, completed, failed or cancelled. The
state is terminal after completion/cancellation; in-flight results can still
arrive after cancellation. Completed jobs can expire or be evicted, so they
are not durable records.

## Status

Existing status, mode, mod_version, minecraft_version, host, port, url,
auth_enabled, in_world, player_name and endpoints fields remain. Added fields:

- execution_mode and capabilities, including active transport and negotiation diagnostic;
- mutation_auth_required;
- queue_depth, queued_commands, active_jobs, max_jobs, max_queued_commands and job_concurrency, processed, failed_commands and optional last_error.

Credentials never appear in this response. A selected HTTP plugin player that
is offline is unavailable; explicitly configured console mode does not require
a player online.

## Errors

Errors retain error and status and add a structured code. Command result codes
separate COMMAND_REJECTED, COMMAND_DENIED, SESSION_CHANGED, TRANSPORT_FAILED,
DISPATCH_EXCEPTION, EXECUTION_TIMEOUT and OUTCOME_UNKNOWN, among others.

| HTTP | Meaning |
|---|---|
| 200 | Status/result/job inspection; inspect individual execution results |
| 202 | Async work accepted |
| 400 | Malformed JSON, UTF-8 or payload shape |
| 401 | Missing/invalid bearer authentication |
| 403 | Browser-origin request or command denied by configured policy |
| 404 | Unknown path, job ID, or expired/evicted job |
| 405 | Wrong method |
| 413 | Body exceeds 64 KiB |
| 415 | POST content type is not application/json |
| 429 | Job/command capacity exhausted |
| 503 | No usable session |
| 500 | Unexpected HTTP handler failure |

## Configuration and in-game commands

Runtime JSON retains host, port, token, authEnabled and loginSummary. Added
options are allowRemotePrivileged (false), elevatedSingleplayer (true),
allowedCommands (["*"]), maxJobs (128), maxQueuedCommands (1024), commandsPerTick
(16), retentionSeconds (600), httpWorkers (8), maxHttpQueue (64),
executionTimeoutSeconds (30), and jobConcurrency (1). Invalid limits are rejected.
Changes made through the existing `/commandapi` host, port, auth, token and login
commands preserve these settings. `/commandapi reload` restarts on the disk
configuration; failure restores the previous endpoint and configuration.
Native commands typed in the game are not elevated by the HTTP feature.

[Security and migration](SECURITY.md) · [Server setup](NATIVE_EXECUTION.md#installing-paperspigot)
