package sk.gkanocz.aisauth.warn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarnServiceTest {

    @Mock
    private WarnRepository warnRepository;
    @Mock
    private WarnThresholdRepository warnThresholdRepository;

    private WarnService warnService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        warnService = new WarnService(warnRepository, warnThresholdRepository);
    }

    @Test
    void addWarnRejectsWarningYourself() {
        assertThatThrownBy(() -> warnService.addWarn("guild-1", "user-1", "user-1", "reason"))
                .isInstanceOf(SelfWarnException.class);
        verifyNoInteractions(warnRepository);
    }

    @Test
    void addWarnSavesWarnWhenModeratorDiffersFromTarget() {
        when(warnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Warn result = warnService.addWarn("guild-1", "user-1", "mod-1", "spamming");

        assertThat(result.getGuildId()).isEqualTo("guild-1");
        assertThat(result.getDiscordId()).isEqualTo("user-1");
        assertThat(result.getModeratorId()).isEqualTo("mod-1");
        assertThat(result.getReason()).isEqualTo("spamming");
    }

    @Test
    void matchingThresholdExcludesNoneAction() {
        WarnThreshold noneThreshold = new WarnThreshold("guild-1", 3, "none");
        when(warnThresholdRepository.findByGuildIdAndWarnLimit("guild-1", 3)).thenReturn(Optional.of(noneThreshold));

        assertThat(warnService.matchingThreshold("guild-1", 3)).isEmpty();
    }

    @Test
    void matchingThresholdReturnsActionableThreshold() {
        WarnThreshold banThreshold = new WarnThreshold("guild-1", 5, "ban");
        when(warnThresholdRepository.findByGuildIdAndWarnLimit("guild-1", 5)).thenReturn(Optional.of(banThreshold));

        assertThat(warnService.matchingThreshold("guild-1", 5)).contains(banThreshold);
    }

    @Test
    void matchingThresholdEmptyWhenNoThresholdConfigured() {
        when(warnThresholdRepository.findByGuildIdAndWarnLimit("guild-1", 2)).thenReturn(Optional.empty());

        assertThat(warnService.matchingThreshold("guild-1", 2)).isEmpty();
    }

    @Test
    void removeWarnThrowsWhenWarnNotFoundInGuild() {
        when(warnRepository.findByIdAndGuildId(42L, "guild-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> warnService.removeWarn(42L, "guild-1"))
                .isInstanceOf(WarnNotFoundException.class);
    }

    @Test
    void removeWarnDeletesWhenFound() {
        Warn warn = new Warn("guild-1", "user-1", "mod-1", "reason");
        when(warnRepository.findByIdAndGuildId(42L, "guild-1")).thenReturn(Optional.of(warn));

        warnService.removeWarn(42L, "guild-1");

        verify(warnRepository).delete(warn);
    }

    @Test
    void clearWarnsReturnsCountThenDeletesAll() {
        when(warnRepository.countByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(3L);

        long cleared = warnService.clearWarns("user-1", "guild-1");

        assertThat(cleared).isEqualTo(3L);
        verify(warnRepository).deleteByDiscordIdAndGuildId("user-1", "guild-1");
    }

    @Test
    void getThresholdsDelegatesToRepositoryOrderedByLimit() {
        List<WarnThreshold> thresholds = List.of(new WarnThreshold("guild-1", 3, "kick"));
        when(warnThresholdRepository.findByGuildIdOrderByWarnLimitAsc("guild-1")).thenReturn(thresholds);

        assertThat(warnService.getThresholds("guild-1")).isEqualTo(thresholds);
    }
}
