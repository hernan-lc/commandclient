# Command API Mod for Fabric

A Minecraft Fabric mod that provides a REST API to execute server commands remotely.

## Features

- **REST API** to execute single or multiple Minecraft commands
- **Configurable authentication** with token support (optional)
- **Java 21** build configuration
- **Multi-version support** - easily change Minecraft version in `gradle.properties`

## API Endpoints

### Execute Command(s)
```
POST /api/execute
```

**Request Body (Single Command):**
```json
{
  "command": "say Hello World"
}
```

**Request Body (Multiple Commands):**
```json
{
  "commands": [
    "say Hello",
    "give @a diamond 1",
    "time set day"
  ]
}
```

**Response:**
```json
{
  "success": true,
  "result": {
    "command": "say Hello World",
    "success": true,
    "output": "Command executed successfully"
  }
}
```

### Server Status
```
GET /api/status
```

**Response:**
```json
{
  "status": "running",
  "mod": "Command API",
  "version": "1.0.0",
  "server_loaded": true,
  "player_count": 0,
  "max_players": 20,
  "server_name": "Fabric Server",
  "motd": "A Fabric Server"
}
```

### Stop Server
```
POST /api/stop
```

## Configuration

### Via gradle.properties

```properties
# API Port (default: 8080)
api_port=8080

# API Token (leave empty for no auth)
api_token=your_secret_token

# Enable/Disable Token Authentication
api_auth_enabled=false
```

### Via Environment Variables

- `API_PORT` - Server port
- `API_TOKEN` - Authentication token
- `API_AUTH_ENABLED` - Enable/disable auth (true/false)

### Via JVM Arguments

```bash
-Dapi.port=8080 -Dapi.token=secret -Dapi.auth.enabled=true
```

## Authentication

If authentication is enabled, include the token in the request header:

```
Authorization: Bearer your_token_here
```

## Building for Different Minecraft Versions

Edit `gradle.properties`:

### For 1.16.5 (Recommended for 1.16.x)
```properties
minecraft_version=1.16.5
yarn_mappings=1.16.5+build.4
loader_version=0.14.21
fabric_version=0.79.0+1.16.5
```

### For 1.17.x
```properties
minecraft_version=1.17.1
yarn_mappings=1.17.1+build.2
loader_version=0.12.12
fabric_version=0.46.1+1.17.1
```

### For 1.18.x
```properties
minecraft_version=1.18.2
yarn_mappings=1.18.2+build.3
loader_version=0.14.9
fabric_version=0.58.0+1.18.2
```

### For 1.19.x
```properties
minecraft_version=1.19.4
yarn_mappings=1.19.4+build.8
loader_version=0.14.21
fabric_version=0.79.0+1.19.4
```

### For 1.20.x
```properties
minecraft_version=1.20.4
yarn_mappings=1.20.4+build.4
loader_version=0.15.7
fabric_version=0.91.2+1.20.4
```

## Build Commands

```bash
# Build the mod
./gradlew build

# Run the development server
./gradlew runClient

# Build for production
./gradlew clean build
```

The built JAR will be in `build/libs/`.

## Requirements

- Java 21
- Gradle 8.5+
- Minecraft 1.16.x - 1.20.x (Fabric supported versions)

## Example Usage with cURL

```bash
# Single command
curl -X POST http://localhost:8080/api/execute \
  -H "Content-Type: application/json" \
  -d '{"command": "say Hello from API!"}'

# Multiple commands
curl -X POST http://localhost:8080/api/execute \
  -H "Content-Type: application/json" \
  -d '{"commands": ["say Hello", "give @a diamond 1"]}'

# With authentication
curl -X POST http://localhost:8080/api/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_token" \
  -d '{"command": "say Hello!"}'

# Check server status
curl http://localhost:8080/api/status
```
