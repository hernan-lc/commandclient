# Build Instructions

How to build the Command API Mod.

## Prerequisites

- Java Development Kit (JDK) 21 or higher
- Internet connection (for downloading dependencies)

## Setup

### 1. Install Java 21

Using SDKMAN:
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.5-tem
```

### 2. Build the Mod

```bash
# Use Java 21
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.5-tem

# Build
./gradlew build
```

The built JAR will be in `build/libs/`.

## Building for Specific Versions

### Minecraft 1.19.4

Edit `gradle.properties`:
```properties
minecraft_version=1.19.4
loader_version=0.14.21
yarn_mappings=1.19.4+build.8
```

Build:
```bash
./gradlew clean build
```

### Minecraft 1.16.1

Edit `gradle.properties`:
```properties
minecraft_version=1.16.1
loader_version=0.11.6
yarn_mappings=1.16.1+build.1
```

Build:
```bash
./gradlew clean build
```

## Gradle Tasks

| Task | Description |
|------|-------------|
| `./gradlew build` | Build the mod |
| `./gradlew clean` | Clean build artifacts |
| `./gradlew runClient` | Run Minecraft with the mod (development) |
| `./gradlew runServer` | Run a dedicated server with the mod |

## Output

After building, the mod JAR will be located at:
```
build/libs/JAVA-1.0.0.jar
```

## Troubleshooting

### Java Version Error

If you get `Unsupported class file major version`:
- Make sure you're using Java 21: `sdk use java 21.0.5-tem`

### Out of Memory

If the build runs out of memory, add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4G -XX:+UseG1GC
```

### Fabric API Not Found

Ensure you have the correct Fabric API version in `build.gradle`:
- For 1.19.4: `0.87.2+1.19.4`
- For 1.16.1: `0.18.0+build.387-1.16.1`

## Installing the Mod

### Single Player
1. Build the mod
2. Copy `build/libs/JAVA-1.0.0.jar` to `.minecraft/mods/`
3. Launch Minecraft with Fabric

### Dedicated Server
1. Build the mod
2. Copy `build/libs/JAVA-1.0.0.jar` to `<server>/mods/`
3. Start the server

## Development

To run Minecraft with the mod during development:

```bash
./gradlew runClient
```

This will launch Minecraft with Fabric and your mod loaded.
