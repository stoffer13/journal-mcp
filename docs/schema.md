# Database Schema

SQLite database at `{journal.data-dir}/journal.db`. All tables created on startup via `CREATE TABLE IF NOT EXISTS`.

## Tables

### captures

```sql
CREATE TABLE IF NOT EXISTS captures (
    id         VARCHAR(36) PRIMARY KEY,
    raw_text   TEXT        NOT NULL,
    created_at VARCHAR(30) NOT NULL
);
```

### entries

```sql
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
```

### entries_fts (FTS5 virtual table)

```sql
CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts5(
    entry_id,
    summary,
    body,
    tags,
    content='entries',
    content_rowid='rowid'
);
```

### reminders

```sql
CREATE TABLE IF NOT EXISTS reminders (
    id          VARCHAR(36)  PRIMARY KEY,
    title       VARCHAR(120) NOT NULL,
    body        TEXT         NOT NULL DEFAULT '',
    remind_at   VARCHAR(30)  NOT NULL,
    done        INTEGER      NOT NULL DEFAULT 0,  -- 0 = pending, 1 = done
    created_at  VARCHAR(30)  NOT NULL
);
```

## FTS5 triggers

Keep the full-text index in sync with the entries table:

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

## Storage conventions

- **Timestamps**: ISO-8601 strings (e.g. `2025-06-01T09:00:00Z`), not SQLite datetime
- **UUIDs**: `VARCHAR(36)`, generated in Java
- **Entities**: JSON array column — `[{"name":"Alice","type":"person"}]`
- **Tags**: comma-separated string — `"backend,performance"`
- **Booleans**: `INTEGER` (0/1)

## Backup

```bash
cp ~/.journal-mcp/journal.db ~/.journal-mcp/journal.db.bak
```
