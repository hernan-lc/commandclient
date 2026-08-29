# Baseline report (pre-refactor, historical)

Recorded before the first multi-version refactor, against commit `d6c4e58`
(single Gradle project, sources in `src/main/java/com/commandapi`). Kept as
history: it explains why several decisions were made. It does **not** describe
the current build — see [BUILD.md](BUILD.md) and [VERSIONS.md](VERSIONS.md).

Environment: Linux, Gradle 8.10.2, Fabric Loom 1.7.4, JDK 21 (the repo's
`gradle/wrapper/gradle-wrapper.jar` was missing and had to be regenerated).

## Results

| Target | Command | Result |
|---|---|---|
| 1.16.1 | `./gradlew build` (defaults from `gradle.properties`) | ✅ `build/libs/commandclient-1.0.0.jar` |
| 1.19.4 | `./gradlew build -Pminecraft_version=1.19.4 -Ploader_version=0.14.21 -Pfabric_version=0.87.2+1.19.4` | ❌ `compileJava` failed |

The 1.19.4 failure:

```
HttpServerManager.java:126: error: cannot find symbol
                mc.player.chat(message);
                         ^
  symbol:   method chat(String)
  location: variable player of type LocalPlayer
```

## Findings carried into the refactor

* **The documented 1.19.4 support did not exist.** `LocalPlayer.chat(String)`
  was removed in 1.19.3; that version needs
  `player.connection.sendChat(..)` / `sendCommand(..)`. This is exactly the
  version-specific call that now lives behind `MinecraftBridge`.
* **Artifacts were not distinguishable.** Both targets would have produced
  `commandclient-1.0.0.jar`, with no Minecraft version in the name.
* **`fabric.mod.json` hardcoded `"minecraft": "1.16.1"`,** so a 1.19.4 build
  would have shipped wrong metadata even if it had compiled.
* **`yarn_mappings` was declared but unused** — the build used
  `loom.officialMojangMappings()`. The property was removed.
* **JDK:** one JDK 21 toolchain compiled both targets; the 1.16.1 target only
  needs `--release 8` bytecode, not a JDK 8 toolchain. `versions.json` records
  `java` (bytecode level) and `buildJava` (JDK that compiles it) separately.
* **`options.release = 8` was global,** which would have pinned modern targets
  to Java 8 bytecode. It is now per version module.
* Behaviour to preserve: client-side mod, `GET /api/status`, `POST /api/chat`,
  `POST /api/execute` alias, Bearer-token auth, chat sent through the local
  client.
