# Command API Mod

A Fabric mod that provides a REST API to execute Minecraft commands remotely.

## Features

- **Remote Command Execution**: Execute Minecraft commands via HTTP API
- **Server Status**: Get current server status (player count, max players)
- **Server Control**: Stop the server remotely
- **Token Authentication**: Secure your API with Bearer token authentication
- **Multi-Version Support**: Compatible with Minecraft 1.16.1 and 1.19.4

## Quick Start

### Build

```bash
./gradlew build
```

### Run

Place the generated JAR in your Fabric mods folder:
- Single Player: `.minecraft/mods/`
- Dedicated Server: `<server>/mods/`

## Configuration

Configure via `gradle.properties` or system properties:

| Property | Default | Description |
|----------|---------|-------------|
| `api_port` | 8080 | HTTP server port |
| `api_token` | (none) | Bearer token for authentication |
| `api_auth_enabled` | false | Enable/disable token authentication |

### Example Properties

```properties
api_port=8080
api_token=your-secret-token
api_auth_enabled=true
```

## API Endpoints

### Execute Command

Execute one or more Minecraft commands.

**Endpoint:** `POST /api/execute`

**Headers:**
- `Content-Type: application/json`
- `Authorization: Bearer <token>` (if auth enabled)

**Request Body (single command):**
```json
{
  "command": "say Hello World"
}
```

**Request Body (multiple commands):**
```json
{
  "commands": ["say Hello", "give @s diamond 1", "time set day"]
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

---

### Server Status

Get current server information.

**Endpoint:** `GET /api/status`

**Headers:**
- `Authorization: Bearer <token>` (if auth enabled)

**Response:**
```json
{
  "status": "running",
  "mod": "Command API",
  "version": "1.0.0",
  "server_loaded": true,
  "player_count": 5,
  "max_players": 20,
  "server_name": "Fabric Server"
}
```

---

### Stop Server

Shutdown the Minecraft server.

**Endpoint:** `POST /api/stop`

**Headers:**
- `Authorization: Bearer <token>` (if auth enabled)

**Response:**
```json
{
  "success": true,
  "message": "Server shutdown initiated"
}
```

## Project Structure

```
src/main/java/com/commandapi/
├── CommandApiMod.java     # Main mod entry point
├── ApiConfig.java         # Configuration handler
└── HttpServerManager.java # HTTP server & API handlers

docs/
├── README.md              # This file
├── API.md                 # API documentation
├── BUILD.md               # Build instructions
└── COMPATIBILITY.md       # Version compatibility
```

## Compatibility

| Minecraft Version | Fabric Loader | Fabric API | Status |
|------------------|---------------|-----------|--------|
| 1.19.4 | 0.14.21 | 0.87.2+1.19.4 | ✅ Tested |
| 1.16.1 | 0.11.6 | 0.18.0+build.387-1.16.1 | ✅ Tested |

## License

MIT License
