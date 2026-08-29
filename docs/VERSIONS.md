<!-- GENERATED FILE. DO NOT EDIT MANUALLY. Run: python3 scripts/generate-version-table.py -->

# Supported Minecraft versions

Command API `1.1.0`. Every row below is a target configured in [`versions.json`](../versions.json) with a module under `versions/`.

The Build column reflects `build/distributions/manifest.json`, produced by `./gradlew buildAllVersions`.

| Minecraft | Java | Fabric Loader | Fabric API | Build | Artifact |
| --------- | ---: | ------------- | ---------- | ----- | -------- |
| 1.16.1 | 8 | 0.11.6 | 0.18.0+build.387-1.16.1 | ✅ | `commandapi-1.1.0+mc1.16.1.jar` |
| 1.19.4 | 17 | 0.14.21 | 0.87.2+1.19.4 | ✅ | `commandapi-1.1.0+mc1.19.4.jar` |
| 1.20.1 | 17 | 0.14.21 | 0.92.11+1.20.1 | ✅ | `commandapi-1.1.0+mc1.20.1.jar` |
| 1.20.4 | 17 | 0.15.6 | 0.97.3+1.20.4 | ✅ | `commandapi-1.1.0+mc1.20.4.jar` |

✅ means the artifact exists in the current build manifest. ❌ means the target is configured but produced no JAR in that build.

## Building a single target

```bash
./gradlew :versions:1.16.1:build
./gradlew :versions:1.19.4:build
./gradlew :versions:1.20.1:build
./gradlew :versions:1.20.4:build
```

See [ADDING_VERSION.md](ADDING_VERSION.md) to add a target and [BUILD.md](BUILD.md) for the build layout.
