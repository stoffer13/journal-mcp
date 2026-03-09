# Security

## Authentication flow

### Bearer token (direct)

1. Client sends `Authorization: Bearer {token}` header with every request
2. `ApiKeyFilter` (extends `OncePerRequestFilter`) extracts the token
3. Token compared against `journal.api-key` using `MessageDigest.isEqual()` (constant-time)
4. On match: sets `UsernamePasswordAuthenticationToken` with principal `"api-client"` and `ROLE_API`
5. On mismatch or missing header: no authentication set — Spring Security returns 401

### OAuth2 authorization code + PKCE (for MCP clients)

MCP clients (Claude Desktop, Cursor, etc.) discover and use OAuth2 to obtain a Bearer token. The access token IS the API key — no separate token storage needed.

**Flow:**

1. Client connects to a protected endpoint and receives `401` with `WWW-Authenticate: Bearer resource_metadata=".../.well-known/oauth-protected-resource"`
2. Client fetches `GET /.well-known/oauth-protected-resource` — returns the authorization server URL
3. Client fetches `GET /.well-known/oauth-authorization-server` — returns RFC 8414 metadata JSON with endpoint URLs and `registration_endpoint`
4. Client registers via `POST /oauth/register` (RFC 7591) — receives a `client_id`
5. Client redirects user to `GET /oauth/authorize?response_type=code&client_id=...&redirect_uri=...&state=...&code_challenge=...&code_challenge_method=S256` — returns HTML login form
6. User submits API key via `POST /oauth/authorize` — server validates key (constant-time), generates authorization code, stores it with PKCE challenge, redirects to `redirect_uri?code=X&state=Z`
7. Client exchanges code via `POST /oauth/token` with `grant_type=authorization_code`, `code`, `redirect_uri`, `code_verifier`, `client_id` — server validates PKCE (S256), returns `{ access_token, token_type: "bearer", expires_in: 86400 }`
8. Client uses `Authorization: Bearer <access_token>` for all subsequent requests — `ApiKeyFilter` validates as usual

**Key details:**

- Authorization codes expire after 5 minutes and are single-use (atomic consume)
- Only `S256` code challenge method is supported
- PKCE verification uses constant-time comparison
- The access token returned is the API key itself
- `@Scheduled` cleanup removes expired codes every 10 minutes

## ApiKeyFilter

- Location: `com.journal.config.ApiKeyFilter`
- Reads `journal.api-key` from config at construction time, stores as `byte[]`
- Runs once per request, before `UsernamePasswordAuthenticationFilter`
- Unchanged by OAuth2 addition — the token IS the API key

## SecurityConfig

- Location: `com.journal.config.SecurityConfig`
- CSRF disabled (stateless API)
- Session creation: `STATELESS`
- Form login and HTTP basic both disabled
- Public endpoints (no auth required):
  - `/actuator/health`
  - `/.well-known/oauth-authorization-server`
  - `/.well-known/oauth-protected-resource`
  - `/oauth/authorize`
  - `/oauth/token`
  - `/oauth/register`
- All other endpoints — require authentication
- No-op `UserDetailsService` bean — suppresses Spring Security's auto-generated password by signaling that auth is handled externally via `ApiKeyFilter`

## OAuth components

| Class | Purpose |
|-------|---------|
| `PkceUtil` | Static utility: `computeS256Challenge(verifier)` → `BASE64URL(SHA-256(verifier))` |
| `AuthorizationCodeStore` | `@Component` with `ConcurrentHashMap`. Stores/consumes codes atomically. 5-min expiry, 10-min cleanup. |
| `OauthMetadataController` | `GET /.well-known/oauth-authorization-server` — RFC 8414 metadata (includes `registration_endpoint`). `GET /.well-known/oauth-protected-resource` — resource metadata pointing to auth server. |
| `OauthController` | `GET/POST /oauth/authorize` + `POST /oauth/token` — full authorization code + PKCE flow |
| `ClientRegistrationController` | `POST /oauth/register` — RFC 7591 dynamic client registration. Accepts client metadata, returns a `client_id`. |

## Configuration

In `application.yml`:

```yaml
journal:
  api-key: ${JOURNAL_API_KEY}
```

Set the `JOURNAL_API_KEY` environment variable before starting the server. The app will fail to start if this variable is not set. The token is a single static string shared by all clients.
