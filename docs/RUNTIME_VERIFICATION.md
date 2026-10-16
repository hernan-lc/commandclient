# Runtime verification

Compilation, integration tests using fake bridges, and actual Minecraft runtime
are separate evidence. Current scope and results are recorded in
[implementation verification](IMPLEMENTATION_VERIFICATION.md). The 1.16.1 and 26.3 native smoke records and Paper 26.3 integration record
come from actual running games. Separate server-variant HTTP smoke records
cover legacy Paper 1.16.5 and modern Paper 26.3 packaging. The remaining runtime matrix is not verified.

Full `runtimeVerified` flags remain false until all relevant checks below run
for that exact version. Never infer runtime support from another version in
the same adapter family or from compilation alone.

## Reproduce native checks

Launch a clean Fabric profile without Fabric API and join a cheats-disabled
singleplayer world. Read its address/token locally. Then run:

```bash
python3 scripts/runtime-smoke.py /path/to/profile/config \
  --level-file '/path/to/profile/saves/TestWorld/level.dat' \
  --output /tmp/native-smoke.json
```

The script rejects missing authentication, confirms native callback results,
checks an invalid command, and waits for an ordered async batch. It does not
print or store the token. It records the tested source commit and the world's
cheats flag. Run it only against an isolated test world: it changes time/weather.

Also check ordinary chat, typed in-game commands and completions, secure config
migration, forbidden command roots, elevatedSingleplayer=false, cancellation,
timeouts, disconnect/reconnect, restart rollback and resource cleanup. Confirm
that the world's state and player context match each command's intended effects.

## Multiplayer checks

Use an isolated, authenticated Paper/Spigot server after its administrator has
accepted Minecraft's EULA. Install only the server plugin first, then configure
permitted roots and explicit commandapi.execute permission grants. Verify:

- compatible HELLO/CAPABILITIES negotiation and automatic active transport;
- unauthorized player denial and unchanged native player permissions;
- cancellation by a separate command-policy plugin, including rewritten commands;
- malformed, duplicate, expired and incompatible frames;
- pending cancellation, disconnect, reconnect and no duplicate execution;
- absent plugin and denied negotiation falling back to normal vanilla packets;
- independent administrator HTTP setup, explicit console mode and token secrecy;
- enable/disable/restart and actual plugin dependency loading.

Keep the console token out of client profiles and packet capture artifacts.
Never set a runtime flag for this based on mocks, packet serialization unit
tests or plugin JAR inspection.

## Matrix

Build every entry in versions.json. Runtime representatives include 1.16.1,
1.16.5, 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.6, 1.21.1, 1.21.11, 26.1 and 26.3,
plus the 1.20.4 typed-payload boundary. Record exact versions, source/artifact
hashes, authentication mode, command results and log excerpts without secrets.
