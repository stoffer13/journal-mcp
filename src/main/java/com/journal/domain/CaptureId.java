package com.journal.domain;

import java.util.UUID;

public record CaptureId(UUID value) {
  public static CaptureId generate() {
    return new CaptureId(UUID.randomUUID());
  }

  public static CaptureId of(String value) {
    return new CaptureId(UUID.fromString(value));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
