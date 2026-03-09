package com.journal.domain;

public record Category(String value) {
    public Category {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Category must not be blank");
        }
        value = value.strip().toLowerCase();
    }

    @Override
    public String toString() {
        return value;
    }
}
