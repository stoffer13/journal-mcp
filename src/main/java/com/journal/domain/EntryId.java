package com.journal.domain;

import java.util.UUID;

public record EntryId(UUID value) {
    public static EntryId generate() {
        return new EntryId(UUID.randomUUID());
    }

    public static EntryId of(String value) {
        return new EntryId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
