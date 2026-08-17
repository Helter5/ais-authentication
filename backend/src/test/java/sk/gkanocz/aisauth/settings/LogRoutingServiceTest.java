package sk.gkanocz.aisauth.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogRoutingServiceTest {

    @Mock
    private LogChannelSubscriptionRepository logChannelSubscriptionRepository;

    private LogRoutingService service;

    @BeforeEach
    void setUp() {
        service = new LogRoutingService(logChannelSubscriptionRepository);
    }

    // ---- channelIdFor ----

    @Test
    void channelIdForReturnsEmptyWhenNoSubscriptionExists() {
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.empty());

        assertThat(service.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).isEmpty();
    }

    @Test
    void channelIdForReturnsTheSubscribedChannel() {
        LogChannelSubscription subscription = new LogChannelSubscription("guild-1", "chan-1", LogEventType.WIPE_RECAP);
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.of(subscription));

        assertThat(service.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).contains("chan-1");
    }

    // ---- listForGuild ----

    @Test
    void listForGuildDelegatesToRepository() {
        LogChannelSubscription subscription = new LogChannelSubscription("guild-1", "chan-1", LogEventType.WIPE_RECAP);
        when(logChannelSubscriptionRepository.findByGuildId("guild-1")).thenReturn(List.of(subscription));

        assertThat(service.listForGuild("guild-1")).containsExactly(subscription);
    }

    // ---- upsert ----

    @Test
    void upsertCreatesANewSubscriptionWhenNoneExists() {
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.empty());

        service.upsert("guild-1", Map.of(LogEventType.WIPE_RECAP, "chan-1"));

        ArgumentCaptor<LogChannelSubscription> captor = ArgumentCaptor.forClass(LogChannelSubscription.class);
        verify(logChannelSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getGuildId()).isEqualTo("guild-1");
        assertThat(captor.getValue().getChannelId()).isEqualTo("chan-1");
        assertThat(captor.getValue().getEventType()).isEqualTo(LogEventType.WIPE_RECAP);
    }

    @Test
    void upsertUpdatesAnExistingSubscriptionsChannel() {
        LogChannelSubscription existing = new LogChannelSubscription("guild-1", "old-chan", LogEventType.WIPE_RECAP);
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.of(existing));

        service.upsert("guild-1", Map.of(LogEventType.WIPE_RECAP, "new-chan"));

        assertThat(existing.getChannelId()).isEqualTo("new-chan");
        verify(logChannelSubscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void upsertDeletesAnExistingSubscriptionWhenChannelIdIsNull() {
        LogChannelSubscription existing = new LogChannelSubscription("guild-1", "old-chan", LogEventType.WIPE_RECAP);
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.of(existing));

        Map<LogEventType, String> assignments = new java.util.HashMap<>();
        assignments.put(LogEventType.WIPE_RECAP, null);
        service.upsert("guild-1", assignments);

        verify(logChannelSubscriptionRepository).delete(existing);
    }

    @Test
    void upsertDeletesAnExistingSubscriptionWhenChannelIdIsBlank() {
        LogChannelSubscription existing = new LogChannelSubscription("guild-1", "old-chan", LogEventType.WIPE_RECAP);
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.of(existing));

        service.upsert("guild-1", Map.of(LogEventType.WIPE_RECAP, "  "));

        verify(logChannelSubscriptionRepository).delete(existing);
    }

    @Test
    void upsertIsANoOpWhenChannelIdIsBlankAndNoSubscriptionExists() {
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.empty());

        service.upsert("guild-1", Map.of(LogEventType.WIPE_RECAP, ""));

        verify(logChannelSubscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(logChannelSubscriptionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void upsertOnlyTouchesEventTypesPresentInTheMap() {
        service.upsert("guild-1", Map.of(LogEventType.WIPE_RECAP, "chan-1"));

        verify(logChannelSubscriptionRepository, never()).findByGuildIdAndEventType("guild-1", LogEventType.SEMESTER_RECAP);
    }
}
