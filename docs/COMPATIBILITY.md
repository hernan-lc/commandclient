# Version Compatibility

Supported Minecraft versions and their compatible dependencies.

## Tested Versions

| Minecraft | Fabric Loader | Fabric API | Yarn Mappings | Status |
|-----------|---------------|------------|---------------|--------|
| 1.19.4 | 0.14.21 | 0.87.2+1.19.4 | 1.19.4+build.8 | ✅ Tested |
| 1.16.1 | 0.11.6 | 0.18.0+build.387-1.16.1 | 1.16.1+build.1 | ✅ Tested |

## Configuration by Version

### Minecraft 1.19.4

`gradle.properties`:
```properties
minecraft_version=1.19.4
loader_version=0.14.21
yarn_mappings=1.19.4+build.8
```

`build.gradle` (fabric_version):
```groovy
def fabric_version = '0.87.2+1.19.4'
```

`fabric.mod.json`:
```json
{
  "depends": {
    "fabricloader": ">=0.14.21",
    "fabric": ">=0.87.2",
    "minecraft": "1.19.4",
    "java": ">=17"
  }
}
```

---

### Minecraft 1.16.1

`gradle.properties`:
```properties
minecraft_version=1.16.1
loader_version=0.11.6
yarn_mappings=1.16.1+build.1
```

`build.gradle` (fabric_version):
```groovy
def fabric_version = '0.18.0+build.387-1.16.1'
```

`fabric.mod.json`:
```json
{
  "depends": {
    "fabricloader": ">=0.11.6",
    "fabric": ">=0.18.0",
    "minecraft": "1.16.1",
    "java": ">=16"
  }
}
```

## API Compatibility

The mod uses reflection to access Minecraft server internals, making it compatible with multiple versions. However, the Fabric API version must match the Minecraft version.

### Supported Features by Version

| Feature | 1.16.1 | 1.19.4 |
|---------|--------|--------|
| Command Execution | ✅ | ✅ |
| Server Status | ✅ | ✅ |
| Server Stop | ✅ | ✅ |
| Token Auth | ✅ | ✅ |

## Java Requirements

| Minecraft Version | Minimum Java |
|-------------------|--------------|
| 1.16.1 | Java 16 |
| 1.19.4 | Java 17 |

## Upcoming Support

The following versions are planned to be tested:
- 1.20.1
- 1.20.4
- 1.21.x

## Notes

- Always use the latest stable Fabric Loader for your Minecraft version
- Yarn mappings are optional but recommended for development
- The mod uses Mojang mappings by default for runtime compatibility
