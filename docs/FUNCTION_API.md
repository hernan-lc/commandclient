# Function API

All function routes live under `/api/functions` and follow the existing
conventions: JSON bodies, `Content-Type: application/json`, no browser
(`Origin`) access, strict path matching. Job ids keep the existing UUID
format; function snapshots add `function`, `total_steps` and
`processed_steps` alongside the existing `total`/`processed` fields.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/functions` | List functions (+ per-file `errors`) |
| GET | `/api/functions/{id}` | Inspect function |
| POST | `/api/functions/{id}/run` | Execute function |
| POST | `/api/functions/{id}/preview` | Preview resolved commands |
| POST | `/api/functions/validate` | Validate submitted function |
| POST | `/api/functions/reload` | Reload functions |
| POST | `/api/functions` | Create function |
| PUT | `/api/functions/{id}` | Update function |
| DELETE | `/api/functions/{id}` | Delete function |

`{id}` is URL-encoded (`events%3Amob_attack`).

## Run

```http
POST /api/functions/events:mob_attack/run
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "args": { "mob": "minecraft:zombie", "count": 5, "title": "Zombie Invasion!" },
  "async": true
}
```

`args` may also be an array for positional binding in declaration order.
Async (default) returns `202` with the job snapshot:

```json
{
  "id": "job-uuid",
  "function": "events:mob_attack",
  "state": "queued",
  "total_steps": 4,
  "processed_steps": 0
}
```

With `"async": false` the run executes synchronously and returns `200`
with the finished snapshot. Poll `/api/jobs/{id}` for progress;
`DELETE /api/jobs/{id}` cancels remaining steps.

## Preview

```http
POST /api/functions/events:mob_attack/preview
```

```json
{ "args": { "mob": "minecraft:zombie", "count": 2 } }
```

Returns `{ "function", "args" (resolved), "commands", "steps", "total_steps" }`.
Permissions and limits are enforced; nothing executes and the world is not
mutated. Argument values are never written to logs (only names/counts).

## Validate / manage

- `POST /api/functions/validate` with `{ "id"?, "format"? ("json" or
  "mcfunction"), "source" }` or `{ "id"?, "definition": {...} }` returns
  `{ "valid", "id"? , "error"?, "code"? }`.
- `POST /api/functions` with `{ "id", "format"?, "source" }` creates
  (201) or replaces via `PUT /api/functions/{id}` (200). Writes are atomic.
- `DELETE /api/functions/{id}` removes the file and registration.
- `POST /api/functions/reload` reloads from disk and reports per-file
  `{ "reloaded", "loaded": [...], "failures": { id: error } }`.

## Auth

Reads follow the existing GET rules (open only when `authEnabled: false`).
`run`, `preview` and `validate` (POST) always require the bearer token.
Create/update/delete/reload additionally require authentication to be
enabled — with `authEnabled: false` they return `403`, so disabling auth
never opens arbitrary file writes.

## Errors

| HTTP | Meaning |
|---|---|
| 200 | Inspection / sync run / preview / validate / management success |
| 201 | Function created |
| 202 | Async run accepted |
| 400 | Bad args, unknown param, validation failure, denied by preview |
| 401 | Missing/invalid bearer token |
| 403 | Command denied by policy, or management with auth disabled |
| 404 | Unknown function id |
| 405 | Wrong method |
| 429 | Job/command capacity exhausted |
| 503 | No usable session (not in world) |

Existing `/api/chat`, `/api/execute` and `/api/jobs` behavior is unchanged.
