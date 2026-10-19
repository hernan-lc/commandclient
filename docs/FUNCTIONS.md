# Functions

Reusable, parameterized Minecraft command functions executed through the
existing CommandClient scheduler, transport selection, authentication and job
system.

## Setup

Functions live under `<config>/commandapi/functions/`. Nested folders map to
namespaces: `events/mob_attack.json` becomes `events:mob_attack`. The
directory is created automatically on first start. Files without a folder map
to the `minecraft` namespace.

Copy any file from `examples/functions/` into that directory (or create one
via `POST /api/functions`), then call `POST /api/functions/reload` — no
restart needed. A file that fails validation is reported in the `errors` map
of `GET /api/functions` while previously valid functions keep working.

## Formats

- Plain `.mcfunction`: command lines plus `#` comments, executed in order.
  A leading `# namespace:name` comment sets the id (the folder still wins).
- Parameterized `.mcfunction` (a CommandClient extension, not vanilla):
  `# @param <name> <type> [default]` headers plus `${name}` placeholders.
- Structured `.json`: schema version, typed parameters and typed steps
  (`command`, `summon`, `title`, `sound`, `particle`, `repeat`, `delay`,
  `function`, `if`). See [FUNCTION_FORMAT.md](FUNCTION_FORMAT.md).

## Running

```bash
curl -X POST "$URL/api/functions/events:mob_attack/run" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"args":{"mob":"minecraft:zombie","count":5},"async":true}'
```

Preview first without touching the world:

```bash
curl -X POST "$URL/api/functions/events:mob_attack/preview" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"args":{"count":2}}'
```

Or use the CLI (`scripts/commandclient.js`):

```bash
node scripts/commandclient.js functions list
node scripts/commandclient.js functions preview events:mob_attack --count 2
node scripts/commandclient.js functions run events:mob_attack --mob minecraft:zombie --count 5
node scripts/commandclient.js jobs get <job-id>
node scripts/commandclient.js jobs cancel <job-id>
```

## Execution guarantees

- Commands run sequentially through the active transport (native
  singleplayer, server plugin, or vanilla chat) with the same ordering,
  session-affinity and result semantics as `/api/chat`.
- `unknown` outcomes stop the run and are never auto-retried (`summon` is
  not idempotent). Confirmed failures are recorded per step.
- Tick delays wait off the game thread in cancellable slices and hold no
  queue capacity while waiting.
- Runs are bounded: max expanded commands, loop iterations, nesting depth,
  delay ticks and total duration. Recursion is rejected.
- Cancellation stops future steps; already-dispatched commands cannot be
  undone.

See [FUNCTION_API.md](FUNCTION_API.md), [FUNCTION_SECURITY.md](FUNCTION_SECURITY.md)
and [FUNCTION_EXAMPLES.md](FUNCTION_EXAMPLES.md).
