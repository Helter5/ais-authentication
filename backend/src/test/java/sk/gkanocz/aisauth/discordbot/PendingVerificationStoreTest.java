package sk.gkanocz.aisauth.discordbot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class PendingVerificationStoreTest {

    private final PendingVerificationStore store = new PendingVerificationStore();

    @Test
    void createReturnsATokenThatResolvesToThePendingEntry() {
        String token = store.create("discord-1", "guild-1", "12345");

        Optional<PendingVerificationStore.Pending> pending = store.get(token);

        assertThat(pending).isPresent();
        assertThat(pending.get().discordId()).isEqualTo("discord-1");
        assertThat(pending.get().guildId()).isEqualTo("guild-1");
        assertThat(pending.get().aisId()).isEqualTo("12345");
    }

    @Test
    void createReturnsDifferentTokensForSuccessiveCalls() {
        String tokenA = store.create("discord-1", "guild-1", "12345");
        String tokenB = store.create("discord-1", "guild-1", "12345");

        assertThat(tokenA).isNotEqualTo(tokenB);
    }

    @Test
    void getReturnsEmptyForAnUnknownToken() {
        assertThat(store.get("unknown-token")).isEmpty();
    }

    @Test
    void removeDeletesTheEntrySoASecondGetReturnsEmpty() {
        String token = store.create("discord-1", "guild-1", "12345");

        store.remove(token);

        assertThat(store.get(token)).isEmpty();
    }

    @Test
    void removingAnUnknownTokenIsANoOp() {
        store.remove("unknown-token");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Object> pendingMap() {
        return (ConcurrentHashMap<String, Object>) org.springframework.test.util.ReflectionTestUtils.getField(store, "pending");
    }

    @Test
    void getReturnsEmptyAndTheEntryIsGoneOnceItHasExpired() throws Exception {
        String token = store.create("discord-1", "guild-1", "12345");
        expireEntry(token);

        Optional<PendingVerificationStore.Pending> pending = store.get(token);

        assertThat(pending).isEmpty();
    }

    @Test
    void creatingANewEntryCleansUpExpiredOnes() throws Exception {
        String expiredToken = store.create("discord-1", "guild-1", "12345");
        expireEntry(expiredToken);

        store.create("discord-2", "guild-1", "99999");

        assertThat(pendingMap()).doesNotContainKey(expiredToken);
    }

    private void expireEntry(String token) throws Exception {
        Object entry = pendingMap().get(token);
        java.lang.reflect.Constructor<?> constructor = entry.getClass().getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object expiredEntry = constructor.newInstance(
                "discord-1", "guild-1", "12345", java.time.Instant.now().minus(Duration.ofSeconds(1)));
        pendingMap().put(token, expiredEntry);
    }
}
