package com.journal.domain;

import java.time.Instant;

public record Capture(CaptureId id, String rawText, Instant createdAt) {
  public static Capture create(String rawText) {
    return new Capture(CaptureId.generate(), rawText, Instant.now());
  }
}
