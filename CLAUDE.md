# journal-mcp

Private MCP server for tech lead journaling — storage and retrieval only, all AI processing happens in the MCP client (Claude).

## Tech stack

Java 25 (`--enable-preview`) · Spring Boot 4.0.3 · Spring AI 1.1.2 (MCP server WebMVC) · SQLite (Xerial JDBC) · Spring Security · Jackson

## Build & run

```bash
./mvnw clean package          # build (no tests yet)
java --enable-preview --enable-native-access=ALL-UNNAMED -jar target/journal-mcp-0.1.0.jar
```

Server starts on port 8080. MCP transport: HTTP/SSE at `/sse`.

## Architecture

```
JournalTools/JournalPrompts → JournalService → SqliteJournalRepository
```

No extra abstraction layers. Domain logic lives on the records themselves.
Schema initialization runs in `SqliteJournalRepository` constructor via `CREATE TABLE IF NOT EXISTS`.

## Package map

```
com.journal
├── JournalApplication          — entry point
├── domain/                     — records & value objects (Entry, Capture, Reminder, Category, EntityRef)
├── storage/                    — SqliteJournalRepository (all SQL, schema init)
├── application/                — JournalService (orchestration)
├── mcp/                        — JournalTools (@Tool methods), JournalPrompts
└── config/                     — SecurityConfig, ApiKeyFilter, McpConfig
```

## Key conventions

- **Value-object IDs**: `EntryId(UUID)`, `CaptureId(UUID)`, `ReminderId(UUID)`, `Category(String)`
- **Records everywhere**: domain types are Java records
- **Timestamps**: ISO-8601 strings in SQLite, `Instant` in Java
- **Entities column**: JSON array of `{name, type}` on `entries` table — no join table
- **Tags**: comma-separated string
- **`--enable-preview`** required in compiler and runtime args

## Configuration

`src/main/resources/application.yml` — key properties:

| Property | Default | Purpose |
|----------|---------|---------|
| `journal.api-key` | *(required)* `$JOURNAL_API_KEY` | Bearer token for auth |
| `journal.data-dir` | `${user.home}/.journal-mcp` | SQLite DB location |
| `server.port` | `8080` | HTTP port |
| `spring.datasource.hikari.maximum-pool-size` | `1` | SQLite single-writer |

## Security

Bearer token auth via `ApiKeyFilter` (constant-time comparison). Stateless, no CSRF, no form login.
`/actuator/health` is public; everything else requires auth.

## Testing

No test suite yet. Verify with:

```bash
./mvnw clean package
```

## Deeper docs

- [docs/schema.md](docs/schema.md) — SQL schema, FTS5, triggers, storage conventions
- [docs/mcp-tools.md](docs/mcp-tools.md) — all MCP tool signatures and prompt definitions
- [docs/domain-model.md](docs/domain-model.md) — value objects, domain records, categories, entity types
- [docs/security.md](docs/security.md) — auth flow, filter behavior, Spring Security setup

