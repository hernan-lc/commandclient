# Function security

Functions reuse the existing command policy, authentication, size limits and
transport permission model. They add no new privilege: a function can only
run commands the `allowedCommands` policy permits, through the same
transport (and its permission checks) as `/api/chat`.

## Interpolation

- No unrestricted replacement: every `${param}` resolves against a declared,
  type-validated value. Unknown or missing parameters are errors, never
  empty strings.
- Newlines, NUL bytes and `;` are rejected in interpolated values, so one
  value cannot split or chain commands.
- Selectors, players, entity ids and resource locations keep narrow grammars;
  quoted/subshell-looking content in those positions is rejected.
- Untyped interpolation containing quotes or `$(` is rejected; declare the
  parameter type instead.
- Title/subtitle text is serialized with JSON encoding, never concatenated
  into JSON by hand.
- A raw `command` step may intentionally contain command syntax, but values
  interpolated into it obey the same type/context rules, and the fully
  expanded command must pass the allowlist and the 512-char limit.

## Files

- Ids must match `namespace:path`; `..`, hidden files and oversized names
  are rejected, and resolved paths must stay under the functions directory
  (symlinks escaping it are skipped).
- Duplicate ids are rejected; per-file failures are reported without
  dropping valid functions.
- API writes are atomic (temp file + rename) and management endpoints
  require authentication to be enabled plus the bearer token.

## Execution

- `unknown` outcomes stop the run and are never retried automatically.
- Bounded: expanded commands, loop iterations, nesting depth, delay ticks,
  execution duration, argument count, file size.
- Preview enforces permissions/limits without executing and logs only
  argument names, never values. Bearer tokens are never printed by the CLI.

## Transports

Singleplayer uses native elevated execution when enabled and authorized;
Paper/Spigot goes through the server-plugin transport and its permission
model; vanilla multiplayer forwards through chat. Dispatch-only transports
report `dispatched`/`unknown`, never confirmed world success.
