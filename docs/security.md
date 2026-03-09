# Security

## Authentication flow

1. Client sends `Authorization: Bearer {token}` header with every request
2. `ApiKeyFilter` (extends `OncePerRequestFilter`) extracts the token
3. Token compared against `journal.api-key` using `MessageDigest.isEqual()` (constant-time)
4. On match: sets `UsernamePasswordAuthenticationToken` with principal `"api-client"` and `ROLE_API`
5. On mismatch or missing header: no authentication set — Spring Security returns 401

## ApiKeyFilter

- Location: `com.journal.config.ApiKeyFilter`
- Reads `journal.api-key` from config at construction time, stores as `byte[]`
- Runs once per request, before `UsernamePasswordAuthenticationFilter`

## SecurityConfig

- Location: `com.journal.config.SecurityConfig`
- CSRF disabled (stateless API)
- Session creation: `STATELESS`
- Form login and HTTP basic both disabled
- `/actuator/health` — public (no auth required)
- All other endpoints — require authentication
- No-op `UserDetailsService` bean — suppresses Spring Security's auto-generated password by signaling that auth is handled externally via `ApiKeyFilter`

## Configuration

In `application.yml`:

```yaml
journal:
  api-key: ${JOURNAL_API_KEY}
```

Set the `JOURNAL_API_KEY` environment variable before starting the server. The app will fail to start if this variable is not set. The token is a single static string shared by all clients.
