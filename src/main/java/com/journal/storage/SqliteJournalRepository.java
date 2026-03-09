package com.journal.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.journal.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SqliteJournalRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public SqliteJournalRepository(DataSource dataSource,
                                    @Value("${journal.data-dir}") String dataDir) throws IOException {
        Files.createDirectories(Paths.get(dataDir));
        this.jdbc = JdbcClient.create(dataSource);
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        initSchema();
    }

    private void initSchema() {
        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS captures (
                id         VARCHAR(36) PRIMARY KEY,
                raw_text   TEXT        NOT NULL,
                created_at VARCHAR(30) NOT NULL
            )
            """).update();

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS entries (
                id          VARCHAR(36)  PRIMARY KEY,
                capture_id  VARCHAR(36),
                category    VARCHAR(50)  NOT NULL,
                summary     VARCHAR(120) NOT NULL,
                body        TEXT         NOT NULL,
                entities    TEXT         NOT NULL DEFAULT '[]',
                tags        TEXT         NOT NULL DEFAULT '',
                created_at  VARCHAR(30)  NOT NULL,
                updated_at  VARCHAR(30)  NOT NULL
            )
            """).update();

        jdbc.sql("""
            CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts5(
                entry_id,
                summary,
                body,
                tags,
                content='entries',
                content_rowid='rowid'
            )
            """).update();

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS reminders (
                id          VARCHAR(36)  PRIMARY KEY,
                title       VARCHAR(120) NOT NULL,
                body        TEXT         NOT NULL DEFAULT '',
                remind_at   VARCHAR(30)  NOT NULL,
                done        INTEGER      NOT NULL DEFAULT 0,
                created_at  VARCHAR(30)  NOT NULL
            )
            """).update();

        jdbc.sql("""
            CREATE TRIGGER IF NOT EXISTS entries_fts_insert AFTER INSERT ON entries BEGIN
                INSERT INTO entries_fts(entry_id, summary, body, tags)
                VALUES (new.id, new.summary, new.body, new.tags);
            END
            """).update();

        jdbc.sql("""
            CREATE TRIGGER IF NOT EXISTS entries_fts_update AFTER UPDATE ON entries BEGIN
                UPDATE entries_fts SET summary = new.summary, body = new.body, tags = new.tags
                WHERE entry_id = new.id;
            END
            """).update();

        jdbc.sql("""
            CREATE TRIGGER IF NOT EXISTS entries_fts_delete AFTER DELETE ON entries BEGIN
                DELETE FROM entries_fts WHERE entry_id = old.id;
            END
            """).update();
    }

    // Capture operations

    public void saveCapture(Capture capture) {
        jdbc.sql("INSERT INTO captures (id, raw_text, created_at) VALUES (?, ?, ?)")
            .params(capture.id().toString(), capture.rawText(), capture.createdAt().toString())
            .update();
    }

    // Entry operations

    public void saveEntry(Entry entry) {
        jdbc.sql("""
            INSERT INTO entries (id, capture_id, category, summary, body, entities, tags, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
            .params(
                entry.id().toString(),
                entry.captureId() != null ? entry.captureId().toString() : null,
                entry.category().value(),
                entry.summary(),
                entry.body(),
                toJson(entry.entities()),
                String.join(",", entry.tags()),
                entry.createdAt().toString(),
                entry.updatedAt().toString()
            )
            .update();
    }

    public void updateEntry(Entry entry) {
        jdbc.sql("UPDATE entries SET body = ?, updated_at = ? WHERE id = ?")
            .params(entry.body(), entry.updatedAt().toString(), entry.id().toString())
            .update();
    }

    public Entry findEntryById(EntryId id) {
        return jdbc.sql("SELECT * FROM entries WHERE id = ?")
            .param(id.toString())
            .query(this::mapEntry)
            .single();
    }

    public List<Entry> searchEntries(String query, String category, String from, String to) {
        var sql = new StringBuilder();
        var params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            sql.append("SELECT e.* FROM entries e JOIN entries_fts f ON e.id = f.entry_id WHERE entries_fts MATCH ?");
            params.add(query);
            if (category != null && !category.isBlank()) {
                sql.append(" AND e.category = ?");
                params.add(category);
            }
            if (from != null && !from.isBlank()) {
                sql.append(" AND e.created_at >= ?");
                params.add(from);
            }
            if (to != null && !to.isBlank()) {
                sql.append(" AND e.created_at <= ?");
                params.add(to);
            }
        } else {
            sql.append("SELECT * FROM entries WHERE 1=1");
            if (category != null && !category.isBlank()) {
                sql.append(" AND category = ?");
                params.add(category);
            }
            if (from != null && !from.isBlank()) {
                sql.append(" AND created_at >= ?");
                params.add(from);
            }
            if (to != null && !to.isBlank()) {
                sql.append(" AND created_at <= ?");
                params.add(to);
            }
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbc.sql(sql.toString())
            .params(params.toArray())
            .query(this::mapEntry)
            .list();
    }

    public List<Entry> findByEntity(String entityName) {
        return jdbc.sql("""
            SELECT * FROM entries e
            WHERE EXISTS (
                SELECT 1 FROM json_each(e.entities) AS j
                WHERE lower(j.value->>'name') = lower(?)
            )
            ORDER BY created_at DESC
            """)
            .param(entityName)
            .query(this::mapEntry)
            .list();
    }

    public List<Entry> listRecentEntries(String category, int limit) {
        if (category != null && !category.isBlank()) {
            return jdbc.sql("SELECT * FROM entries WHERE category = ? ORDER BY created_at DESC LIMIT ?")
                .params(category, limit)
                .query(this::mapEntry)
                .list();
        }
        return jdbc.sql("SELECT * FROM entries ORDER BY created_at DESC LIMIT ?")
            .param(limit)
            .query(this::mapEntry)
            .list();
    }

    // Reminder operations

    public void saveReminder(Reminder reminder) {
        jdbc.sql("INSERT INTO reminders (id, title, body, remind_at, done, created_at) VALUES (?, ?, ?, ?, ?, ?)")
            .params(
                reminder.id().toString(),
                reminder.title(),
                reminder.body(),
                reminder.remindAt().toString(),
                reminder.done() ? 1 : 0,
                reminder.createdAt().toString()
            )
            .update();
    }

    public void completeReminder(ReminderId id) {
        jdbc.sql("UPDATE reminders SET done = 1 WHERE id = ?")
            .param(id.toString())
            .update();
    }

    public List<Reminder> listDueReminders() {
        String now = Instant.now().toString();
        return jdbc.sql("SELECT * FROM reminders WHERE remind_at <= ? AND done = 0 ORDER BY remind_at ASC")
            .param(now)
            .query(this::mapReminder)
            .list();
    }

    public List<Reminder> listUpcomingReminders(int withinHours) {
        String now = Instant.now().toString();
        String until = Instant.now().plusSeconds(withinHours * 3600L).toString();
        return jdbc.sql("SELECT * FROM reminders WHERE remind_at > ? AND remind_at <= ? AND done = 0 ORDER BY remind_at ASC")
            .params(now, until)
            .query(this::mapReminder)
            .list();
    }

    // Mappers

    private Entry mapEntry(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String captureIdStr = rs.getString("capture_id");
        return new Entry(
            EntryId.of(rs.getString("id")),
            captureIdStr != null && !captureIdStr.isBlank() ? CaptureId.of(captureIdStr) : null,
            new Category(rs.getString("category")),
            rs.getString("summary"),
            rs.getString("body"),
            fromJson(rs.getString("entities")),
            parseTags(rs.getString("tags")),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    private Reminder mapReminder(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Reminder(
            ReminderId.of(rs.getString("id")),
            rs.getString("title"),
            rs.getString("body"),
            Instant.parse(rs.getString("remind_at")),
            rs.getInt("done") == 1,
            Instant.parse(rs.getString("created_at"))
        );
    }

    private String toJson(List<EntityRef> entities) {
        try {
            return mapper.writeValueAsString(entities);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<EntityRef> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return List.of(tags.split(",")).stream()
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
    }
}
