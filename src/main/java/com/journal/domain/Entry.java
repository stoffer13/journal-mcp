package com.journal.domain;

import java.time.Instant;
import java.util.List;

public record Entry(
    EntryId id,
    CaptureId captureId,
    Category category,
    String summary,
    String body,
    List<EntityRef> entities,
    List<String> tags,
    Instant createdAt,
    Instant updatedAt) {
  public Entry {
    entities = entities == null ? List.of() : List.copyOf(entities);
    tags = tags == null ? List.of() : List.copyOf(tags);
  }

  public static Entry create(
      CaptureId captureId,
      Category category,
      String summary,
      String body,
      List<EntityRef> entities,
      List<String> tags) {
    Instant now = Instant.now();
    return new Entry(
        EntryId.generate(), captureId, category, summary, body, entities, tags, now, now);
  }

  public Entry withAppendedBody(String additionalText) {
    return new Entry(
        id,
        captureId,
        category,
        summary,
        body + "\n\n" + additionalText,
        entities,
        tags,
        createdAt,
        Instant.now());
  }
}
