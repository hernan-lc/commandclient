# Implementation verification

Reviewed baseline: `752904aa20a59d49a208e333fad48b219ce4535b`.
At the start of this work, local HEAD and fetched origin/main both matched it.
All changes are on `fix/secure-native-transports` in logical phase commits.

## Automated evidence

The following commands actually ran locally, using JDK 21 for Gradle and the
manifest's provisioned compiler toolchains for target modules:

```text
./gradlew :common:test
./gradlew :server-adapter:build
./gradlew buildAllVersions collectServerArtifact verifyBuilds
node --test docs/example-client.test.js
python3 scripts/generate-version-table.py --check
```

Shared Java suite: 114 tests, zero failures/errors/skips. Server suite: eight
tests, zero failures/errors/skips. These include real socket HTTP tests and
mocked Bukkit dispatcher/permission/event/lifecycle tests. The Node example
also has a real HTTP credential-scope test. They do not stand in for a real
Paper server or Minecraft client-server negotiation test.

All 15 Fabric targets compiled and produced production JARs. The artifact
verifier passed 301 checks. Plugin packaging verification passed: metadata,
Java 21 class level, bundled shared classes, unique ZIP entries, and absence
of Bukkit/Fabric/Gson/test dependency classes. Gson is supplied by the server.

## Runtime scope

Minecraft 26.3 was actually launched with Fabric Loader, without Fabric API,
in a copied local world with `allowCommands=false`. Its integrated-server
native HTTP execution passed six checks: authenticated status, unauthenticated
privileged rejection, confirmed native execution, invalid-command rejection,
ordered async completion, and cheats-disabled operation.

The machine-readable record is [26.3-native.json](../runtime-evidence/26.3-native.json).
It identifies the tested code commit. This is a native-execution smoke test,
not full certification of every feature or later changes. The initial runtime
attempt exposed an interface mixin failure missed by compilation; the corrected
build loaded and the native checks passed.

| Minecraft | Adapter | Compile/artifact | Actual runtime evidence |
|---|---|---|---|
| 1.16.1 | legacy-chat | passed | native HTTP smoke, cheats disabled |
| 1.16.5 | legacy-chat | passed | not verified |
| 1.18.2 | legacy-chat | passed | not verified |
| 1.19.2 | signed-chat | passed | not verified |
| 1.19.4 | network-chat | passed | not verified |
| 1.20.1 | network-chat | passed | not verified |
| 1.20.4 | network-chat | passed | not verified |
| 1.20.6 | network-chat | passed | not verified |
| 1.21.1 | network-chat | passed | not verified |
| 1.21.4 | network-chat | passed | not verified |
| 1.21.8 | network-chat | passed | not verified |
| 1.21.11 | network-chat | passed | not verified |
| 26.1 | network-chat | passed | not verified |
| 26.2 | network-chat | passed | not verified |
| 26.3 | network-chat | passed | native HTTP and Paper integration smoke |

Full `runtimeVerified` flags stay false: chat/completions, disconnect/reconnect
and the complete per-version runtime matrix have not been comprehensively verified.

## Outstanding verification and limits

After user approval of Minecraft's EULA, isolated Paper 26.3 build 180 actually
started with the standalone unshaded plugin. The real Fabric 26.3 client
negotiated server-plugin transport, received native permission rejection while
non-op, successfully dispatched after an explicit test administrator grant,
and honored cancellation by a separate policy plugin. Disconnect reset
capabilities and returned HTTP 503. Disabling the bridge, reconnecting, and
waiting for negotiation timeout selected vanilla; enabling and reconnecting
restored plugin transport. The client HTTP URL stayed the same throughout.
Client capabilities remained console=false while administrator console HTTP
was independently enabled. See [paper-26.3.json](../runtime-evidence/paper-26.3.json).

This proves runtime packaging/dependency loading and the recorded Paper 26.3
paths. Real Spigot and other Paper versions, malicious-frame network tests,
and the full representative singleplayer matrix are separate outstanding
checks until corresponding evidence is recorded.

A tick budget bounds starts of commands, not the duration of an already-running
expensive command. Cancellation cannot undo an in-flight dispatch. Bukkit
results establish dispatch, not plugin world effects. Protocol clocks need to
be reasonably synchronized. Folia is unsupported. Root allowlists delegate
nested behavior through explicitly permitted execute/function/alias commands.

CI definitions now build/test both products and publish them separately.
Local success does not mean hosted CI already passed; inspect the PR checks.
This evidence supports review, not a claim that the entire production/runtime
Definition of Done has been met.

## Continuation on 2026-10-16

The initial 1.16.1 runtime attempt reached a real local world but exposed a
Gson linkage error in HTTP result copying: that Minecraft version provides
Gson 2.8.0, whose deepCopy method is not public. The implementation now copies
result trees through the baseline Gson API and compiles/tests common against
Gson 2.8.0 declared in versions.json. A regression test mutates returned result
copies and confirms the retained result remains unchanged. The rerun passed all six checks in a real cheats-disabled world; see
[1.16.1-native.json](../runtime-evidence/1.16.1-native.json). This also exercises
the legacy numeric-permission and Brigadier callback path. Other versions
remain compile-verified only until their individual runtime checks run.

The JVM shutdown hook now uses the normal stop path to cancel jobs, release
workers and close the listener. Its regression test invokes that same hook and
checks the port, job state and non-interruption of the pending dispatcher.
