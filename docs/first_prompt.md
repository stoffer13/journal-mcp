# journal-mcp — Project Specification

Build a private MCP server for tech lead journaling, written in Java using Spring Boot and Spring AI.
The server is responsible for storage and retrieval only. All AI processing is done by the MCP client (Claude).

---

## Technology stack

- Java 25 (GraalVM 25.0.2), with `--enable-preview`
- Spring Boot 4.0.3
- Spring AI 1.1.2 (`spring-ai-starter-mcp-server-webmvc`)
- Spring JDBC (`JdbcClient`) for database access
- Xerial SQLite JDBC (latest stable) — bundles the native SQLite library, no separate install needed
- Jackson for JSON serialization of entity lists (JavaTimeModule)
- No Anthropic SDK on the server — the server makes no AI calls

---

## Security

Every request must carry an `Authorization: Bearer {token}` header.
The token is a single hardcoded string in `application.yml` under `journal.api-key`,
intended to be changed manually before deploying.
Use constant-time comparison to validate the token.
Spring Security configured as stateless — no sessions, no CSRF, no form login.

---

## Storage

Single SQLite database file at `{journal.data-dir}/journal.db`.
All tables and indexes are created on application startup via `CREATE TABLE IF NOT EXISTS`.
Timestamps stored as ISO-8601 strings. UUIDs stored as VARCHAR.
Entities stored as a JSON array column on the `entries` table — no join table needed.

### Schema

```sql
CREATE TABLE IF NOT EXISTS captures (
    id         VARCHAR(36) PRIMARY KEY,
    raw_text   TEXT        NOT NULL,
    created_at VARCHAR(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS entries (
    id          VARCHAR(36)  PRIMARY KEY,
    capture_id  VARCHAR(36),
    category    VARCHAR(50)  NOT NULL,
    summary     VARCHAR(120) NOT NULL,
    body        TEXT         NOT NULL,
    entities    TEXT         NOT NULL DEFAULT '[]',  -- JSON array of {name, type}
    tags        TEXT         NOT NULL DEFAULT '',    -- comma-separated
    created_at  VARCHAR(30)  NOT NULL,
    updated_at  VARCHAR(30)  NOT NULL
);

CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts5(
    entry_id,
    summary,
    body,
    tags,
    content='entries',
    content_rowid='rowid'
);

CREATE TABLE IF NOT EXISTS reminders (
    id          VARCHAR(36)  PRIMARY KEY,
    title       VARCHAR(120) NOT NULL,
    body        TEXT         NOT NULL DEFAULT '',
    remind_at   VARCHAR(30)  NOT NULL,
    done        INTEGER      NOT NULL DEFAULT 0,  -- 0 = pending, 1 = done
    created_at  VARCHAR(30)  NOT NULL
);
```

FTS5 triggers to keep the index in sync with the entries table:

```sql
CREATE TRIGGER IF NOT EXISTS entries_fts_insert AFTER INSERT ON entries BEGIN
    INSERT INTO entries_fts(entry_id, summary, body, tags)
    VALUES (new.id, new.summary, new.body, new.tags);
END;

CREATE TRIGGER IF NOT EXISTS entries_fts_update AFTER UPDATE ON entries BEGIN
    UPDATE entries_fts SET summary = new.summary, body = new.body, tags = new.tags
    WHERE entry_id = new.id;
END;

CREATE TRIGGER IF NOT EXISTS entries_fts_delete AFTER DELETE ON entries BEGIN
    DELETE FROM entries_fts WHERE entry_id = old.id;
END;
```

Backup: `cp journal.db journal.db.bak`

---

## Domain model

Use value objects for all IDs and constrained types. Pattern:

```java
record EntryId(UUID value)
record CaptureId(UUID value)
record ReminderId(UUID value)
record Category(String value)   // validated, lowercased
```

### Capture

Raw unprocessed input from a voice note or free-text session. Stored as-is for audit.
The MCP client extracts structured entries from it and links them back via `captureId`.

Fields: `CaptureId id`, `String rawText`, `Instant createdAt`

### Entry

A single categorized journal observation, either extracted from a capture or added directly.

Fields: `EntryId id`, `CaptureId captureId` (nullable), `Category category`, `String summary`,
`String body`, `List<EntityRef> entities`, `List<String> tags`, `Instant createdAt`, `Instant updatedAt`

`EntityRef` is a record with `String name` and `String type` (person / system / ticket / project).

Known categories (open for extension):
- `tech_debt` — code quality, shortcuts, things to refactor
- `team_eval` — team member performance, behavior, growth
- `feature_refinement` — feature ideas, requirement changes, UX thoughts
- `todo` — concrete action items
- `decision` — architectural, product, or process decisions
- `observation` — general insights and patterns
- `blocker` — things actively blocking progress

### Reminder

A time-bound note that should surface again at a future point.

Fields: `ReminderId id`, `String title`, `String body`, `Instant remindAt`,
`boolean done`, `Instant createdAt`

Reminders are considered due when `remindAt` is in the past and `done` is false.

---

## MCP tools

### Write

**`saveCapture(rawText)`**
Stores raw capture text. Returns the `captureId`. Always called first in a capture session.

**`addEntry(captureId, category, summary, body, entitiesJson, tags)`**
Adds one structured entry. `captureId` is optional (empty string for direct entries).
`entitiesJson` is a JSON array of `{name, type}` objects.
`tags` is a comma-separated string.
Called once per extracted item by the client after processing a capture.

**`extendEntry(entryId, additionalText)`**
Appends text to an existing entry body with a blank line separator. Updates `updated_at`.

**`addReminder(title, body, remindAt)`**
Creates a new reminder. `remindAt` is an ISO-8601 datetime string.

**`completeReminder(reminderId)`**
Sets `done = 1` on the reminder.

### Read

**`searchEntries(query, category, from, to)`**
All parameters optional.
When `query` is provided, uses FTS5:
`SELECT e.* FROM entries e JOIN entries_fts f ON e.id = f.entry_id WHERE f.entries_fts MATCH ?`
Category and date filters applied as additional WHERE clauses.
Sorted by `created_at` descending.

**`findByEntity(entityName)`**
Uses SQLite's `json_each()` to search the entities JSON column:
`WHERE EXISTS (SELECT 1 FROM json_each(e.entities) AS j WHERE lower(j.value->>'name') = lower(?))`
Sorted by `created_at` descending.

**`listRecentEntries(category, limit)`**
Returns the N most recent entries, optionally filtered by category. Max 50.

**`listDueReminders()`**
Returns all reminders where `remind_at <= now` and `done = 0`,
sorted by `remind_at` ascending (most overdue first).

**`listUpcomingReminders(withinHours)`**
Returns all pending reminders due within the next N hours,
sorted by `remind_at` ascending.

**`listCategories()`**
Returns all known category names with a one-line description of each.

---

## MCP prompts

Prompts live server-side so all clients (iOS, Desktop, Claude Code) share identical behavior.
Tuning categorization or query logic means updating the server, not each client.

### `capture` prompt

Arguments: `input` (required) — the raw text or transcription.

Instructs the client Claude to:
1. Call `saveCapture` with the full raw text.
2. Read the text and identify every distinct topic, observation, or action item.
3. For each item determine category, summary (max 120 chars), body, entities, and tags.
4. Show a brief preview list: `[category] summary (entities)` — one line each.
5. Ask for confirmation with a short yes/no question (the user may be driving).
6. On confirmation, call `addEntry` once per item using the `captureId` from step 1.
7. Also flag any items that look like reminders and ask if they should be saved as reminders too.

The client Claude is the intelligence — the server stores what the client tells it to store.

### `journal_query` prompt

Arguments: `question` (required) — what the user wants to know.

Instructs the client Claude to:
1. Use `searchEntries`, `findByEntity`, or `listRecentEntries` as appropriate.
2. Also call `listDueReminders` if the question is about what's pending or what needs doing.
3. Synthesize a direct answer, noting how things evolved if entries span a time range.
4. Keep the response concise — the user may be listening while driving.

---

## Package structure

```
com.journal
├── JournalApplication
├── domain/
│   ├── Entry, EntryId
│   ├── Capture, CaptureId
│   ├── Reminder, ReminderId
│   ├── Category, Categories
│   └── EntityRef
├── storage/
│   └── SqliteJournalRepository
├── application/
│   └── JournalService
├── mcp/
│   ├── JournalTools
│   └── JournalPrompts
└── config/
    ├── ApiKeyFilter
    ├── SecurityConfig
    └── McpConfig
```

No extra abstraction layers. `JournalTools` delegates directly to `JournalService`.
`JournalService` delegates directly to `SqliteJournalRepository`.
Logic that does not touch storage lives on the domain records themselves.
Schema initialization runs in `SqliteJournalRepository` constructor via `CREATE TABLE IF NOT EXISTS`.

---

## Configuration (`application.yml`)

```yaml
spring:
  application:
    name: journal-mcp
  datasource:
    url: jdbc:sqlite:${journal.data-dir}/journal.db
    driver-class-name: org.sqlite.JDBC
    hikari:
      maximum-pool-size: 1   # SQLite does not handle concurrent writes
  ai:
    mcp:
      server:
        name: journal-mcp
        version: 0.1.0

journal:
  api-key: "change-me-before-deploying"
  data-dir: ${user.home}/.journal-mcp

server:
  port: 8080
```

---

## Deployment notes (for README)

- Build: `./mvnw clean package -DskipTests`
- Run: `java -jar journal-mcp-*.jar`
- Reverse proxy: Caddy with automatic TLS in front of `localhost:8080`
- MCP client config: HTTP/SSE transport at `https://journal.yourdomain.com/sse`
  with `Authorization: Bearer {api-key}` header
- Backup: `cp {data-dir}/journal.db {data-dir}/journal.db.bak`
  or automate with a nightly rsync to a separate location