package com.journal.domain;

import java.time.Instant;

public record Reminder(
    ReminderId id,
    String title,
    String body,
    Instant remindAt,
    boolean done,
    Instant createdAt
) {
    public static Reminder create(String title, String body, Instant remindAt) {
        return new Reminder(ReminderId.generate(), title, body, remindAt, false, Instant.now());
    }

    public Reminder completed() {
        return new Reminder(id, title, body, remindAt, true, createdAt);
    }

    public boolean isDue() {
        return !done && remindAt.isBefore(Instant.now());
    }
}
