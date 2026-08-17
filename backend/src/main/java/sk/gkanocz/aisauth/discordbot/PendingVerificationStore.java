package sk.gkanocz.aisauth.discordbot;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the AIS ID a user is about to verify with while they confirm/cancel it via the /verify
 * embed's buttons - lets a fat-fingered AIS ID be caught before an email actually goes out. In
 * memory and short-lived like OAuthExchangeStore's exchange codes; a bot restart in the few seconds
 * between /verify and clicking Confirm is an acceptable edge case (the button just reports expired).
 */
@Component
class PendingVerificationStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<String, Entry> pending = new ConcurrentHashMap<>();

    String create(String discordId, String guildId, String aisId) {
        cleanup();
        String token = HexFormat.of().formatHex(randomBytes());
        pending.put(token, new Entry(discordId, guildId, aisId, Instant.now().plus(TTL)));
        return token;
    }

    Optional<Pending> get(String token) {
        Entry entry = pending.get(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(new Pending(entry.discordId(), entry.guildId(), entry.aisId()));
    }

    void remove(String token) {
        pending.remove(token);
    }

    private void cleanup() {
        pending.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private record Entry(String discordId, String guildId, String aisId, Instant expiresAt) {
    }

    record Pending(String discordId, String guildId, String aisId) {
    }
}
