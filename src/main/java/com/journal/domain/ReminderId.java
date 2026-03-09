package com.journal.domain;

import java.util.UUID;

public record ReminderId(UUID value) {
    public static ReminderId generate() {
        return new ReminderId(UUID.randomUUID());
    }

    public static ReminderId of(String value) {
        return new ReminderId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
