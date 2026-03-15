# API Reference

Complete API documentation for the Command API Mod.

## Base URL

```
http://localhost:8080
```

## Authentication

If authentication is enabled, all requests must include the `Authorization` header:

```
Authorization: Bearer <your-token>
```

## Endpoints

---

## POST /api/execute

Execute one or more Minecraft commands.

### Request

**Method:** `POST`  
**Content-Type:** `application/json`

#### Single Command

```json
{
  "command": "say Hello World"
}
```

#### Multiple Commands

```json
{
  "commands": [
    "say Hello",
    "give @s diamond 1",
    "time set day"
  ]
}
```

### Response

**Success (200)**
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

**Multiple Commands Response**
```json
{
  "success": true,
  "results": [
    {
      "command": "say Hello",
      "success": true,
      "output": "Command executed successfully"
    },
    {
      "command": "give @s diamond 1",
      "success": true,
      "output": "Command executed successfully"
    }
  ]
}
```

**Error Responses**

| Status | Description |
|--------|-------------|
| 400 | Missing 'command' or 'commands' field |
| 401 | Unauthorized (invalid or missing token) |
| 405 | Method not allowed (not POST) |
| 500 | Internal server error |

---

## GET /api/status

Get current server status.

### Request

**Method:** `GET`  
**Headers:** `Authorization: Bearer <token>` (if enabled)

### Response

**Success (200)**
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

**Error Responses**

| Status | Description |
|--------|-------------|
| 401 | Unauthorized |

---

## POST /api/stop

Shutdown the Minecraft server.

### Request

**Method:** `POST`  
**Headers:** `Authorization: Bearer <token>` (if enabled)

### Response

**Success (200)**
```json
{
  "success": true,
  "message": "Server shutdown initiated"
}
```

The server will shutdown 1 second after the request.

**Error Responses**

| Status | Description |
|--------|-------------|
| 401 | Unauthorized |
| 405 | Method not allowed (not POST) |

---

## Error Response Format

All errors follow this format:

```json
{
  "error": "Error message",
  "status": 401
}
```

## Examples

### cURL

```bash
# Execute command
curl -X POST http://localhost:8080/api/execute \
  -H "Content-Type: application/json" \
  -d '{"command": "say Hello!"}'

# With authentication
curl -X POST http://localhost:8080/api/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer my-secret-token" \
  -d '{"command": "say Hello!"}'

# Get status
curl http://localhost:8080/api/status

# Stop server
curl -X POST http://localhost:8080/api/stop
```

### JavaScript (Fetch)

```javascript
// Execute command
const response = await fetch('http://localhost:8080/api/execute', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer my-secret-token'
  },
  body: JSON.stringify({ command: 'say Hello!' })
});

const data = await response.json();
console.log(data);
```
