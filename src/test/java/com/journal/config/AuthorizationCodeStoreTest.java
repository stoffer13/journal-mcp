package com.journal.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.journal.config.AuthorizationCodeStore.PendingAuthorization;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationCodeStoreTest {

  private AuthorizationCodeStore store;

  @BeforeEach
  void setUp() {
    store = new AuthorizationCodeStore();
  }

  @Test
  void storeAndConsume_returnsStoredValue() {
    store.store("code-1", "challenge-abc", "http://localhost/callback");

    PendingAuthorization result = store.consume("code-1");

    assertThat(result).isNotNull();
    assertThat(result.codeChallenge()).isEqualTo("challenge-abc");
    assertThat(result.redirectUri()).isEqualTo("http://localhost/callback");
  }

  @Test
  void consume_returnsNullOnSecondCall() {
    store.store("code-2", "challenge", "http://localhost/callback");

    assertThat(store.consume("code-2")).isNotNull();
    assertThat(store.consume("code-2")).isNull();
  }

  @Test
  void consume_unknownCode_returnsNull() {
    assertThat(store.consume("nonexistent")).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void cleanup_removesExpiredEntries() throws Exception {
    // Use reflection to insert a backdated entry
    Field codesField = AuthorizationCodeStore.class.getDeclaredField("codes");
    codesField.setAccessible(true);
    var codes = (ConcurrentHashMap<String, PendingAuthorization>) codesField.get(store);

    // Insert entry created 10 minutes ago (expired — beyond 5 min limit)
    codes.put(
        "expired-code",
        new PendingAuthorization(
            "challenge", "http://localhost/cb", Instant.now().minusSeconds(600)));

    store.cleanup();

    assertThat(store.consume("expired-code")).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void cleanup_keepsNonExpiredEntries() throws Exception {
    Field codesField = AuthorizationCodeStore.class.getDeclaredField("codes");
    codesField.setAccessible(true);
    var codes = (ConcurrentHashMap<String, PendingAuthorization>) codesField.get(store);

    // Insert entry created 1 minute ago (not expired)
    codes.put(
        "fresh-code",
        new PendingAuthorization(
            "challenge", "http://localhost/cb", Instant.now().minusSeconds(60)));

    store.cleanup();

    assertThat(store.consume("fresh-code")).isNotNull();
  }
}
