package com.mboapocket.mboapocket_app.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TwoFactorStore {

    private static final long EXPIRY_MS = 10 * 60 * 1000; // 10 minutes

    private record Entry(String code, String email, Instant expiry) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public String createSession(String email) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        String tempToken = UUID.randomUUID().toString();
        store.put(tempToken, new Entry(code, email, Instant.now().plusMillis(EXPIRY_MS)));
        return tempToken + "|" + code; // retourne les deux pour usage interne
    }

    // Retourne l'email si valide, sinon null
    public String verify(String tempToken, String code) {
        Entry entry = store.get(tempToken);
        if (entry == null) return null;
        if (Instant.now().isAfter(entry.expiry())) {
            store.remove(tempToken);
            return null;
        }
        if (!entry.code().equals(code)) return null;
        store.remove(tempToken);
        return entry.email();
    }
}
