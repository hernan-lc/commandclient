# Function examples

All files under `examples/functions/` are valid and covered by tests. Copy
the ones you want into `<config>/commandapi/functions/` (optionally under a
namespace folder) and reload. Nothing is installed automatically, and none
of the examples is destructive (no `ban`, `op`, `stop` or world edits).

| File | What it does |
|---|---|
| `spawn_zombie.json` | Spawns N mobs at a position |
| `mob_attack.json` | Repeat-summon plus title and dragon growl |
| `boss_event.json` | Title, sound, tick delay, summon, conditional message |
| `welcome_player.json` | Title, level-up sound, particles |
| `title_and_subtitle.json` | Plain title/subtitle announcement |
| `play_sound.json` | Parameterized sound with volume/pitch |
| `firework_event.json` | Title, particles, blast sound, chat line |
| `weather_change.mcfunction` | Parameterized `weather` + confirmation |
| `teleport_player.json` | `tp` with validated player and position |
| `gift_reward.json` | `give` with validated item and amount |

## Streaming integration (events → Minecraft)

```javascript
async function triggerMobAttack({
  url,
  token,
  mob = "minecraft:zombie",
  count = 1,
  title = "Mob Attack!"
}) {
  const response = await fetch(
    `${url}/api/functions/events:mob_attack/run`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        args: {
          mob,
          count,
          title,
          subtitle: `${count} mobs incoming!`
        },
        async: true
      })
    }
  );

  if (!response.ok) {
    throw new Error(`Function failed: ${response.status}`);
  }

  return response.json();
}
```

The caller passes validated arguments only — no Minecraft command syntax
knowledge required. Check the returned job with `GET /api/jobs/{id}` and
never retry `unknown` outcomes automatically.

## CLI equivalents

```bash
node scripts/commandclient.js functions run events:mob_attack \
  --mob minecraft:zombie --count 5 --title "Zombie Invasion!"
node scripts/commandclient.js functions preview events:mob_attack --count 2
node scripts/commandclient.js functions validate ./examples/functions/mob_attack.json
```
