package sk.gkanocz.aisauth.thesiscounter;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.managers.channel.ChannelManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThesisCounterServiceTest {

    private static final String GUILD_ID = "guild-1";

    private final ThesisCounterConfigRepository repository = mock(ThesisCounterConfigRepository.class);
    private final ThesisCounterService service = new ThesisCounterService(repository);

    private Guild guild;
    private GuildChannel channel;
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ChannelManager channelManager;

    @BeforeEach
    void setUp() {
        guild = mock(Guild.class);
        channel = mock(GuildChannel.class);
        channelManager = mock(ChannelManager.class, Mockito.RETURNS_SELF);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getGuildChannelById("channel-1")).thenReturn(channel);
        when(channel.getName()).thenReturn("general");
        when(channel.getManager()).thenReturn(channelManager);
    }

    // --- channelName() template rendering - the actual per-day display logic ---

    @Test
    void channelNameUsesNameFormatWhileDaysRemain() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.of(2026, 12, 25), "general", null, null);

        String name = ThesisCounterService.channelName(5, config);

        assertThat(name).isEqualTo("5-dni-do-bp");
    }

    @Test
    void channelNameUsesSingularDayWordForExactlyOneDay() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "DP", LocalDate.of(2026, 12, 25), "general", null, null);

        String name = ThesisCounterService.channelName(1, config);

        assertThat(name).isEqualTo("1-den-do-dp");
    }

    @Test
    void channelNameFallsBackToTodayFormatOnceTargetDateArrives() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.of(2026, 1, 1), "general", null, null);

        String name = ThesisCounterService.channelName(0, config);

        assertThat(name).isEqualTo("dnes-bp");
    }

    @Test
    void channelNameHonorsCustomFormatsOverDefaults() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "DP", LocalDate.of(2026, 6, 1), "general",
                "custom-{days}-{target_date}", "custom-today-{label}");

        assertThat(ThesisCounterService.channelName(3, config)).isEqualTo("custom-3-01.06.2026");
        assertThat(ThesisCounterService.channelName(0, config)).isEqualTo("custom-today-dp");
    }

    @Test
    void channelNameTreatsBlankCustomFormatAsUnset() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.of(2026, 12, 25), "general", "   ", "   ");

        assertThat(ThesisCounterService.channelName(2, config)).isEqualTo("2-dni-do-bp");
        assertThat(ThesisCounterService.channelName(0, config)).isEqualTo("dnes-bp");
    }

    // --- daysRemaining() ---

    @Test
    void daysRemainingFloorsAtZeroOncePastTargetDate() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now().minusDays(3), "general", null, null);

        assertThat(service.daysRemaining(config)).isZero();
    }

    // --- addCounter() ---

    @Test
    void addCounterSavesConfigAndRenamesChannel() {
        LocalDate target = LocalDate.now().plusDays(10);
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ThesisCounterConfig result = service.addCounter(guild, "channel-1", "BP", target, null, null);

        assertThat(result.getGuildId()).isEqualTo(GUILD_ID);
        assertThat(result.getChannelId()).isEqualTo("channel-1");
        assertThat(result.getOriginalChannelName()).isEqualTo("general");
        assertThat(result.isActive()).isTrue();
        verify(channelManager).setName("10-dni-do-bp");
        verify(channelManager).queue();
    }

    @Test
    void addCounterRejectsRoomThatAlreadyHasACounter() {
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-1"))
                .thenReturn(Optional.of(mock(ThesisCounterConfig.class)));

        assertThatThrownBy(() -> service.addCounter(guild, "channel-1", "BP", LocalDate.now().plusDays(1), null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already has a thesis counter");
        verify(repository, never()).save(any());
    }

    @Test
    void addCounterRejectsLabelOtherThanBpOrDp() {
        assertThatThrownBy(() -> service.addCounter(guild, "channel-1", "MASTERS", LocalDate.now().plusDays(1), null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("BP or DP");
    }

    @Test
    void addCounterRejectsTargetDateInThePast() {
        assertThatThrownBy(() -> service.addCounter(guild, "channel-1", "BP", LocalDate.now().minusDays(1), null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("past");
    }

    @Test
    void addCounterRejectsUnknownChannel() {
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "missing-channel")).thenReturn(Optional.empty());
        when(guild.getGuildChannelById("missing-channel")).thenReturn(null);

        assertThatThrownBy(() -> service.addCounter(guild, "missing-channel", "BP", LocalDate.now().plusDays(1), null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Channel not found");
    }

    // --- editCounter() ---

    @Test
    void editCounterUpdatesLabelAndTargetDateInPlaceWhenChannelUnchanged() {
        ThesisCounterConfig existing = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now().plusDays(20), "general", null, null);
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-1")).thenReturn(Optional.of(existing));

        ThesisCounterConfig result = service.editCounter(
                guild, "channel-1", "channel-1", "DP", LocalDate.now().plusDays(5), null, null);

        assertThat(result.getLabel()).isEqualTo("DP");
        assertThat(result.getChannelId()).isEqualTo("channel-1");
        verify(channelManager).setName("5-dni-do-dp");
    }

    @Test
    void editCounterMovesToNewChannelAndRestoresOldChannelName() {
        ThesisCounterConfig existing = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now().plusDays(20), "general", null, null);
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-1")).thenReturn(Optional.of(existing));
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-2")).thenReturn(Optional.empty());

        GuildChannel newChannel = mock(GuildChannel.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ChannelManager newChannelManager = mock(ChannelManager.class, Mockito.RETURNS_SELF);
        when(guild.getGuildChannelById("channel-2")).thenReturn(newChannel);
        when(newChannel.getName()).thenReturn("random-room");
        when(newChannel.getManager()).thenReturn(newChannelManager);

        ThesisCounterConfig result = service.editCounter(
                guild, "channel-1", "channel-2", "BP", LocalDate.now().plusDays(5), null, null);

        assertThat(result.getChannelId()).isEqualTo("channel-2");
        assertThat(result.getOriginalChannelName()).isEqualTo("random-room");
        // old room's name gets restored to what it was before the counter took it over
        verify(channelManager).setName("general");
        verify(channelManager).queue(any(), any());
        // new room gets the counter's rename applied
        verify(newChannelManager).setName("5-dni-do-bp");
    }

    @Test
    void editCounterRejectsMovingToARoomThatAlreadyHasACounter() {
        ThesisCounterConfig existing = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now().plusDays(20), "general", null, null);
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-1")).thenReturn(Optional.of(existing));
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-2"))
                .thenReturn(Optional.of(mock(ThesisCounterConfig.class)));

        assertThatThrownBy(() -> service.editCounter(
                guild, "channel-1", "channel-2", "BP", LocalDate.now().plusDays(5), null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already has a thesis counter");
    }

    @Test
    void editCounterThrowsWhenNoExistingCounterForChannel() {
        when(repository.findByGuildIdAndChannelId(GUILD_ID, "channel-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editCounter(
                guild, "channel-1", "channel-1", "BP", LocalDate.now().plusDays(5), null, null))
                .isInstanceOf(ThesisCounterConfigNotFoundException.class);
    }

    // --- removeCounter() ---

    @Test
    void removeCounterRestoresOriginalNameAndDeletesRow() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now().plusDays(5), "general", null, null);

        service.removeCounter(guild, config);

        verify(channelManager).setName("general");
        verify(channelManager).queue(any(), any());
        verify(repository).deleteByIdAndGuildId(config.getId(), GUILD_ID);
    }

    @Test
    void removeCounterStillDeletesRowWhenChannelNoLongerExists() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "missing-channel", "BP", LocalDate.now().plusDays(5), "general", null, null);
        when(guild.getGuildChannelById("missing-channel")).thenReturn(null);

        service.removeCounter(guild, config);

        verify(repository).deleteByIdAndGuildId(config.getId(), GUILD_ID);
    }

    // --- applyDailyRename() ---

    @Test
    void applyDailyRenameSkipsWhenChannelNoLongerExists() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "missing-channel", "BP", LocalDate.now().plusDays(5), "general", null, null);
        when(guild.getGuildChannelById("missing-channel")).thenReturn(null);

        service.applyDailyRename(guild, config);

        verify(guild).getGuildChannelById("missing-channel");
        Mockito.verifyNoMoreInteractions(channelManager);
    }

    @Test
    void applyDailyRenameSkipsRenameCallWhenNameAlreadyMatches() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now().plusDays(5), "5-dni-do-bp", null, null);
        when(channel.getName()).thenReturn("5-dni-do-bp");

        service.applyDailyRename(guild, config);

        verify(channelManager, never()).setName(any());
    }

    @Test
    void applyDailyRenameDeactivatesConfigOnceTargetDateArrives() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now(), "general", null, null);
        assertThat(config.isActive()).isTrue();

        service.applyDailyRename(guild, config);

        assertThat(config.isActive()).isFalse();
        verify(channelManager).setName("dnes-bp");
    }

    @Test
    void applyDailyRenameLeavesActiveConfigAloneWhileDaysRemain() {
        ThesisCounterConfig config = new ThesisCounterConfig(
                GUILD_ID, "channel-1", "BP", LocalDate.now().plusDays(3), "general", null, null);

        service.applyDailyRename(guild, config);

        assertThat(config.isActive()).isTrue();
    }
}
