package com.journal.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.journal.application.JournalService;
import com.journal.domain.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JournalTools {

    private final JournalService service;
    private final ObjectMapper mapper;

    public JournalTools(JournalService service) {
        this.service = service;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // Write tools

    @Tool(description = "Store raw capture text (voice note or free-text). Returns the captureId to use in subsequent addEntry calls.")
    public String saveCapture(String rawText) {
        Capture capture = service.saveCapture(rawText);
        return capture.id().toString();
    }

    @Tool(description = "Add one structured journal entry. captureId is optional (empty string for direct entries). entitiesJson is a JSON array of {name, type} objects. tags is a comma-separated string. category must be one of: tech_debt, team_eval, feature_refinement, todo, decision, observation, blocker.")
    public String addEntry(String captureId, String category, String summary, String body,
                           String entitiesJson, String tags) {
        CaptureId cid = (captureId == null || captureId.isBlank()) ? null : CaptureId.of(captureId);
        List<EntityRef> entities = parseEntities(entitiesJson);
        List<String> tagList = parseTags(tags);
        Entry entry = service.addEntry(cid, new Category(category), summary, body, entities, tagList);
        return entry.id().toString();
    }

    @Tool(description = "Append additional text to an existing entry body, separated by a blank line. Updates updated_at.")
    public String extendEntry(String entryId, String additionalText) {
        Entry entry = service.extendEntry(EntryId.of(entryId), additionalText);
        return "Extended entry " + entry.id() + ", updated_at=" + entry.updatedAt();
    }

    @Tool(description = "Create a new reminder. remindAt must be an ISO-8601 datetime string (e.g. 2025-06-01T09:00:00Z).")
    public String addReminder(String title, String body, String remindAt) {
        Reminder reminder = service.addReminder(title, body, Instant.parse(remindAt));
        return reminder.id().toString();
    }

    @Tool(description = "Mark a reminder as done.")
    public String completeReminder(String reminderId) {
        service.completeReminder(ReminderId.of(reminderId));
        return "Reminder " + reminderId + " marked as done.";
    }

    // Read tools

    @Tool(description = "Search journal entries. All parameters optional. query uses FTS5 full-text search. category filters by category. from and to are ISO-8601 date strings for date range.")
    public String searchEntries(String query, String category, String from, String to) {
        List<Entry> entries = service.searchEntries(query, category, from, to);
        return toJson(entries.stream().map(this::entryToMap).toList());
    }

    @Tool(description = "Find all entries mentioning a specific entity by name (person, system, ticket, or project).")
    public String findByEntity(String entityName) {
        List<Entry> entries = service.findByEntity(entityName);
        return toJson(entries.stream().map(this::entryToMap).toList());
    }

    @Tool(description = "List the N most recent journal entries. category is optional. limit defaults to 10, max 50.")
    public String listRecentEntries(String category, int limit) {
        if (limit <= 0) limit = 10;
        List<Entry> entries = service.listRecentEntries(category, limit);
        return toJson(entries.stream().map(this::entryToMap).toList());
    }

    @Tool(description = "List all due reminders (remind_at in the past, not yet done), sorted by most overdue first.")
    public String listDueReminders() {
        List<Reminder> reminders = service.listDueReminders();
        return toJson(reminders.stream().map(this::reminderToMap).toList());
    }

    @Tool(description = "List all pending reminders due within the next N hours, sorted by soonest first.")
    public String listUpcomingReminders(int withinHours) {
        List<Reminder> reminders = service.listUpcomingReminders(withinHours);
        return toJson(reminders.stream().map(this::reminderToMap).toList());
    }

    @Tool(description = "List all known journal categories with their descriptions.")
    public String listCategories() {
        Map<String, String> categories = service.listCategories();
        return toJson(categories);
    }

    // Helpers

    private List<EntityRef> parseEntities(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
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

    private Map<String, Object> entryToMap(Entry e) {
        return Map.of(
            "id", e.id().toString(),
            "captureId", e.captureId() != null ? e.captureId().toString() : "",
            "category", e.category().value(),
            "summary", e.summary(),
            "body", e.body(),
            "entities", e.entities(),
            "tags", e.tags(),
            "createdAt", e.createdAt().toString(),
            "updatedAt", e.updatedAt().toString()
        );
    }

    private Map<String, Object> reminderToMap(Reminder r) {
        return Map.of(
            "id", r.id().toString(),
            "title", r.title(),
            "body", r.body(),
            "remindAt", r.remindAt().toString(),
            "done", r.done(),
            "createdAt", r.createdAt().toString()
        );
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
