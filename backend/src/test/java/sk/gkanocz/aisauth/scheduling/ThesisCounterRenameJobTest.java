package sk.gkanocz.aisauth.scheduling;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.Test;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterConfig;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterConfigRepository;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ThesisCounterRenameJobTest {

    private final ThesisCounterConfigRepository repository = mock(ThesisCounterConfigRepository.class);
    private final ThesisCounterService thesisCounterService = mock(ThesisCounterService.class);
    private final DiscordBotService discordBotService = mock(DiscordBotService.class);

    private final ThesisCounterRenameJob job =
            new ThesisCounterRenameJob(repository, thesisCounterService, discordBotService);

    @Test
    void doesNothingWhenBotIsNotConnected() {
        when(discordBotService.jda()).thenReturn(Optional.empty());

        job.renameActiveCounters();

        verifyNoInteractions(repository);
        verifyNoInteractions(thesisCounterService);
    }

    @Test
    void renamesEveryActiveCounterWhoseGuildIsStillJoined() {
        JDA jda = mock(JDA.class);
        Guild guild = mock(Guild.class);
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById("guild-1")).thenReturn(guild);

        ThesisCounterConfig config = new ThesisCounterConfig(
                "guild-1", "channel-1", "BP", LocalDate.now().plusDays(5), "general", null, null);
        when(repository.findByActiveTrue()).thenReturn(List.of(config));

        job.renameActiveCounters();

        verify(thesisCounterService).applyDailyRename(guild, config);
    }

    @Test
    void skipsConfigWhoseGuildIsNoLongerJoined() {
        JDA jda = mock(JDA.class);
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById("guild-gone")).thenReturn(null);

        ThesisCounterConfig config = new ThesisCounterConfig(
                "guild-gone", "channel-1", "BP", LocalDate.now().plusDays(5), "general", null, null);
        when(repository.findByActiveTrue()).thenReturn(List.of(config));

        job.renameActiveCounters();

        verify(thesisCounterService, never()).applyDailyRename(any(), any());
    }

    @Test
    void oneConfigFailingDoesNotStopTheRest() {
        JDA jda = mock(JDA.class);
        Guild guildOne = mock(Guild.class);
        Guild guildTwo = mock(Guild.class);
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById("guild-1")).thenReturn(guildOne);
        when(jda.getGuildById("guild-2")).thenReturn(guildTwo);

        ThesisCounterConfig failing = new ThesisCounterConfig(
                "guild-1", "channel-1", "BP", LocalDate.now().plusDays(5), "general", null, null);
        ThesisCounterConfig healthy = new ThesisCounterConfig(
                "guild-2", "channel-2", "DP", LocalDate.now().plusDays(3), "general", null, null);
        when(repository.findByActiveTrue()).thenReturn(List.of(failing, healthy));
        org.mockito.Mockito.doThrow(new RuntimeException("Discord API unreachable"))
                .when(thesisCounterService).applyDailyRename(eq(guildOne), eq(failing));

        job.renameActiveCounters();

        verify(thesisCounterService).applyDailyRename(guildOne, failing);
        verify(thesisCounterService).applyDailyRename(guildTwo, healthy);
    }
}
