package com.journal.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthorizationCodeStore {

    public record PendingAuthorization(String codeChallenge, String redirectUri, Instant createdAt) {}

    private static final long EXPIRY_SECONDS = 300; // 5 minutes

    private final ConcurrentHashMap<String, PendingAuthorization> codes = new ConcurrentHashMap<>();

    public void store(String code, String codeChallenge, String redirectUri) {
        codes.put(code, new PendingAuthorization(codeChallenge, redirectUri, Instant.now()));
    }

    public PendingAuthorization consume(String code) {
        return codes.remove(code);
    }

    @Scheduled(fixedRate = 600_000) // every 10 minutes
    public void cleanup() {
        var cutoff = Instant.now().minusSeconds(EXPIRY_SECONDS);
        codes.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
