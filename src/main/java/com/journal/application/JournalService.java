package com.journal.application;

import com.journal.domain.*;
import com.journal.storage.SqliteJournalRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JournalService {

    private final SqliteJournalRepository repository;

    public JournalService(SqliteJournalRepository repository) {
        this.repository = repository;
    }

    public Capture saveCapture(String rawText) {
        Capture capture = Capture.create(rawText);
        repository.saveCapture(capture);
        return capture;
    }

    public Entry addEntry(CaptureId captureId, Category category, String summary,
                           String body, List<EntityRef> entities, List<String> tags) {
        Entry entry = Entry.create(captureId, category, summary, body, entities, tags);
        repository.saveEntry(entry);
        return entry;
    }

    public Entry extendEntry(EntryId entryId, String additionalText) {
        Entry existing = repository.findEntryById(entryId);
        Entry updated = existing.withAppendedBody(additionalText);
        repository.updateEntry(updated);
        return updated;
    }

    public Reminder addReminder(String title, String body, java.time.Instant remindAt) {
        Reminder reminder = Reminder.create(title, body, remindAt);
        repository.saveReminder(reminder);
        return reminder;
    }

    public void completeReminder(ReminderId reminderId) {
        repository.completeReminder(reminderId);
    }

    public List<Entry> searchEntries(String query, String category, String from, String to) {
        return repository.searchEntries(query, category, from, to);
    }

    public List<Entry> findByEntity(String entityName) {
        return repository.findByEntity(entityName);
    }

    public List<Entry> listRecentEntries(String category, int limit) {
        int capped = Math.min(limit, 50);
        return repository.listRecentEntries(category, capped);
    }

    public List<Reminder> listDueReminders() {
        return repository.listDueReminders();
    }

    public List<Reminder> listUpcomingReminders(int withinHours) {
        return repository.listUpcomingReminders(withinHours);
    }

    public java.util.Map<String, String> listCategories() {
        return Categories.DESCRIPTIONS;
    }
}
