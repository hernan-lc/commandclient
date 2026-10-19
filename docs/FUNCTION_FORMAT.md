# Function format

## `.mcfunction`

```mcfunction
# utilities:welcome
say Welcome!
title @a title {"text":"Welcome!","color":"gold"}
playsound minecraft:entity.player.levelup master @a ~ ~ ~ 1 1
```

Lines starting with `#` are comments and never execute. Blank lines are
ignored. Every other line is one command, run in order through the command
scheduler (`/` prefix optional).

Parameterized variant (CommandClient extension):

```mcfunction
# @param mob entity minecraft:zombie
# @param title string "Mob Attack!"
# @param subtitle string "Prepare yourself!"

summon ${mob} ~ ~1 ~
title @a title {"text":"${title}","color":"gold"}
title @a subtitle {"text":"${subtitle}","color":"yellow"}
```

`# @param <name> <type> [default]`: a parameter with no default is required.
`$${name}` renders a literal `${name}`. Unknown or missing parameters are
errors, never silent empty strings.

## JSON

```json
{
  "schemaVersion": 1,
  "name": "mob_attack",
  "description": "Spawn mobs and announce an attack",
  "parameters": {
    "mob": { "type": "entity", "default": "minecraft:zombie" },
    "count": { "type": "integer", "min": 1, "max": 20, "default": 1 },
    "title": { "type": "string", "default": "Mob Attack!" }
  },
  "steps": [
    { "type": "repeat", "times": "${count}", "steps": [
      { "type": "summon", "entity": "${mob}", "position": "~ ~1 ~" }
    ] },
    { "type": "title", "target": "@a", "title": "${title}",
      "subtitle": "Prepare yourself!", "fadeInTicks": 10,
      "stayTicks": 60, "fadeOutTicks": 20 },
    { "type": "sound", "sound": "minecraft:entity.ender_dragon.growl",
      "source": "master", "target": "@a", "volume": 1, "pitch": 1 }
  ]
}
```

`schemaVersion` must be `1`. Unknown step types are errors. Validation
errors name the file, step index and field.

## Parameter types

| Type | Example | Rules |
|---|---|---|
| `string` | `"Zombie Attack"` | max 512 chars, no newlines |
| `integer` | `5` | optional `min`/`max` |
| `number` | `1.5` | optional `min`/`max` |
| `boolean` | `true` | `true`/`false` |
| `entity` | `"minecraft:zombie"` | resource location |
| `player` | `"Steve"` | 1-16 alphanumerics/underscore |
| `selector` | `"@a"` | `@p/@a/@r/@s/@e` with optional `[...]` |
| `position` | `"~ ~1 ~"` | relative/absolute coordinates |
| `enum` | `"easy"` | must list `values` |
| `resource_location` | `"minecraft:entity.player.levelup"` | `namespace:path` |

Declaration keys: `type` (required), `default`, `required`, `min`, `max`,
`values` (for `enum`), `description`. A parameter with a `default` is
optional unless `required: true`.

## Step types

- `command`: `{ "value": "time set day" }` — raw command; interpolated
  values follow type/context rules, and the expanded command must pass the
  command allowlist.
- `summon`: `{ "entity", "position", "nbt"? }` → `summon`.
- `title`: `{ "target", "title"?, "subtitle"?, "fadeInTicks"?,
  "stayTicks"?, "fadeOutTicks"? }` → `title ... times` plus `title`/`subtitle`
  commands. Text is JSON-serialized, never hand-assembled.
- `sound`: `{ "sound", "source"?, "target"?, "volume"?, "pitch"?,
  "position"? }` → `playsound`. Volume 0-16, pitch 0-2 (exclusive of 0).
- `particle`: `{ "particle", "position"?, "count"? }` → `particle`.
  Count 1-10000.
- `repeat`: `{ "times", "steps" }` — `times` is a literal or `${param}`,
  bounded by the loop limit.
- `delay`: `{ "ticks" }` — bounded tick wait, cancellable, off-thread.
- `function`: `{ "name", "args"? }` — nested call with cycle detection and
  depth limit. Args may reference outer parameters.
- `if`: `{ "condition": { "parameter", "operator", "value" },
  "then": [...], "else"?: [...] }` — operators `eq ne gt gte lt lte` over
  resolved parameters only. World state cannot be queried.
