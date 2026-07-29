package sk.gkanocz.aisauth.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class OAuthExchangeStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private record PendingState(Instant expiresAt) {
    }

    private record PendingToken(String accessToken, String refreshToken, Instant expiresAt) {
    }

    public record ExchangedTokens(String accessToken, String refreshToken) {
    }

    private final Map<String, PendingState> pendingStates = new ConcurrentHashMap<>();
    private final Map<String, PendingToken> exchangeCodes = new ConcurrentHashMap<>();

    String createState() {
        cleanupStates();
        String state = randomHex(16);
        pendingStates.put(state, new PendingState(Instant.now().plusSeconds(15 * 60)));
        return state;
    }

    boolean consumeState(String state) {
        PendingState pending = pendingStates.remove(state);
        return pending != null && Instant.now().isBefore(pending.expiresAt());
    }

    String putToken(String accessToken, String refreshToken) {
        cleanupTokens();
        String code = randomHex(32);
        exchangeCodes.put(code, new PendingToken(accessToken, refreshToken, Instant.now().plusSeconds(5 * 60)));
        return code;
    }

    ExchangedTokens consumeToken(String code) {
        PendingToken pending = exchangeCodes.remove(code);
        if (pending == null || Instant.now().isAfter(pending.expiresAt())) {
            return null;
        }
        return new ExchangedTokens(pending.accessToken(), pending.refreshToken());
    }

    private void cleanupStates() {
        pendingStates.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
    }

    private void cleanupTokens() {
        exchangeCodes.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
