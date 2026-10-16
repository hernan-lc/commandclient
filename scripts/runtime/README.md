# Isolated runtime tools

These sources are test-only: they are never included in Fabric or server JARs.
They drive the normal Minecraft connect/create/disconnect APIs on the client
thread and Bukkit plugin enable/disable APIs on the server thread. They require
local ownership/attach access to explicitly selected temporary JVM processes.
Never attach them to a user's active world or production server.

Compile RuntimeAgent and ServerAgent with the oldest JDK supported by the test
process (`--release 17` for the tested development launches), and Attach with
`--add-modules jdk.attach`. Package each agent separately with manifest
`Agent-Class: RuntimeAgent` or `Agent-Class: ServerAgent`.

```sh
java --add-modules jdk.attach -cp /tmp/runtime-classes Attach \
  TEST_PID /tmp/runtime-agent.jar disconnect
java --add-modules jdk.attach -cp /tmp/runtime-classes Attach \
  TEST_PID /tmp/runtime-agent.jar connect:127.0.0.1:TEST_PORT
```

Client actions: `load:WORLD_FOLDER` opens an existing legacy world; `newworld` opens the normal creation screen; `create` calls its
creation action after it is ready; `disconnect`; `connect:ADDRESS`. Server
actions: `disable` and `enable` the CommandApiBridge plugin, scheduling via the
BridgeSmokeFixture owner. Reflection is isolated to this development test tool;
production version integration uses generated, statically mapped calls.

BridgeSmokeFixture is a separate temporary plugin. It grants only
commandapi.execute to the artificial BridgeTest user, records operator/native
permission state, and cancels `/weather rain` to verify third-party events.
It never belongs in a published release or normal server installation.

Use existing accepted Minecraft EULA terms before running any temporary server.
Bind test servers to loopback and keep all bearer tokens in private temporary
config files. Run scripts/runtime-smoke.py for native JSON evidence. The HTTP
example has a separately executable Node test.
