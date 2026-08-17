package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthExchangeStoreTest {

    private final OAuthExchangeStore store = new OAuthExchangeStore();

    @Test
    void createStateReturnsAUniqueValueEachTime() {
        assertThat(store.createState()).isNotEqualTo(store.createState());
    }

    @Test
    void consumeStateAcceptsAFreshlyCreatedState() {
        String state = store.createState();

        assertThat(store.consumeState(state)).isTrue();
    }

    @Test
    void consumeStateIsSingleUse() {
        String state = store.createState();
        store.consumeState(state);

        assertThat(store.consumeState(state)).isFalse();
    }

    @Test
    void consumeStateRejectsAnUnknownState() {
        assertThat(store.consumeState("never-issued")).isFalse();
    }

    @Test
    void putTokenReturnsAUniqueCodeEachTime() {
        assertThat(store.putToken("a1", "r1")).isNotEqualTo(store.putToken("a2", "r2"));
    }

    @Test
    void consumeTokenReturnsTheAccessAndRefreshTokenForAFreshCode() {
        String code = store.putToken("access-1", "refresh-1");

        OAuthExchangeStore.ExchangedTokens tokens = store.consumeToken(code);

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-1");
    }

    @Test
    void consumeTokenIsSingleUse() {
        String code = store.putToken("access-1", "refresh-1");
        store.consumeToken(code);

        assertThat(store.consumeToken(code)).isNull();
    }

    @Test
    void consumeTokenRejectsAnUnknownCode() {
        assertThat(store.consumeToken("never-issued")).isNull();
    }
}
