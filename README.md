# Command API Mod

A Fabric mod that provides a REST API to execute Minecraft commands remotely.

## Quick Links

- **[📖 Documentation](docs/README.md)** - Main documentation
- **[🔌 API Reference](docs/API.md)** - Complete API endpoints
- **[🔨 Build Guide](docs/BUILD.md)** - Build instructions
- **[✅ Compatibility](docs/COMPATIBILITY.md)** - Version support

## Features

- **Remote Command Execution**: Execute Minecraft commands via HTTP API
- **Server Status**: Get current server status (player count, max players)
- **Server Control**: Stop the server remotely
- **Token Authentication**: Secure your API with Bearer token authentication
- **Multi-Version Support**: Compatible with Minecraft 1.16.1 and 1.19.4

## Build

```bash
./gradlew build
```

## Quick Start

1. Build the mod: `./gradlew build`
2. Copy `build/libs/JAVA-1.0.0.jar` to your mods folder
3. Configure in `gradle.properties`:
   - `api_port=8080` - HTTP server port
   - `api_token=your-token` - Authentication token
   - `api_auth_enabled=true` - Enable authentication

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/execute` | POST | Execute commands |
| `/api/status` | GET | Get server status |
| `/api/stop` | POST | Stop server |

See [API.md](docs/API.md) for full documentation.

## Project Structure

```
src/main/java/com/commandapi/
├── CommandApiMod.java      # Main mod entry point
├── ApiConfig.java          # Configuration handler
└── HttpServerManager.java  # HTTP server & API handlers

docs/
├── README.md               # Main documentation
├── API.md                  # API reference
├── BUILD.md                # Build instructions
└── COMPATIBILITY.md        # Version compatibility
```

## Supported Versions

| Minecraft | Status |
|-----------|--------|
| 1.19.4 | ✅ Tested |
| 1.16.1 | ✅ Tested |

## License

MIT License
