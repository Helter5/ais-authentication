package sk.gkanocz.aisauth.wipe;

import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.directory.StudentDirectoryService;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.EventLogEmbedSender;
import sk.gkanocz.aisauth.discordbot.RecapChannelPoster;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WipeServiceTest {

    @Mock
    private VerifiedUserRepository verifiedUserRepository;
    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private StudentDirectoryService studentDirectoryService;
    @Mock
    private VerificationProperties verificationProperties;
    @Mock
    private DashboardAuditLogger dashboardAuditLogger;
    @Mock
    private RecapChannelPoster recapChannelPoster;
    @Mock
    private LogRoutingService logRoutingService;
    @Mock
    private EventLogEmbedSender eventLogEmbedSender;
    @Mock
    private Guild guild;

    private WipeService wipeService;

    @BeforeEach
    void setUp() {
        wipeService = new WipeService(
                verifiedUserRepository, guildSettingsService, adminSettingsService,
                studentDirectoryService, verificationProperties, dashboardAuditLogger, null, recapChannelPoster,
                logRoutingService, eventLogEmbedSender);
        lenient().when(guild.getId()).thenReturn("guild-1");
    }

    @Test
    void startRejectsWhenVerifiedRoleIsNotConfigured() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);

        assertThatThrownBy(() -> wipeService.start(guild, false, List.of(), "actor-1", "actor"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Verified role not set. Configure it in Settings first.");
    }

    @Test
    void startRejectsWhenInactiveRoleIsNotConfigured() {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId("verified-role");
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);

        assertThatThrownBy(() -> wipeService.start(guild, false, List.of(), "actor-1", "actor"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Inactive role not set. Configure it in Settings first.");
    }

    @Test
    void startRejectsWhenThereAreNoVerifiedUsers() {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId("verified-role");
        settings.setInactiveRoleId("inactive-role");
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);
        when(verifiedUserRepository.findByGuildId("guild-1")).thenReturn(List.of());

        assertThatThrownBy(() -> wipeService.start(guild, false, List.of(), "actor-1", "actor"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("No verified users in database.");
    }

    @Test
    void startRejectsWhenWipeLogChannelIsNotConfigured() {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId("verified-role");
        settings.setInactiveRoleId("inactive-role");
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);
        when(verifiedUserRepository.findByGuildId("guild-1"))
                .thenReturn(List.of(new VerifiedUser("ais-1", "discord-1", "guild-1", "user@stuba.sk")));
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wipeService.start(guild, false, List.of(), "actor-1", "actor"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Wipe log channel not set. Configure it in Settings first.");
    }

    @Test
    void startRejectsWhenAWipeIsAlreadyRunningForTheGuild() throws InterruptedException {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId("verified-role");
        settings.setInactiveRoleId("inactive-role");
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);
        when(verifiedUserRepository.findByGuildId("guild-1"))
                .thenReturn(List.of(new VerifiedUser("ais-1", "discord-1", "guild-1", "user@stuba.sk")));
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).thenReturn(Optional.of("channel-1"));

        // Blocks the background wipe worker so "running" stays true until this test releases it,
        // instead of racing the async completion of runWipe(). reachedRoleLookup additionally
        // confirms the worker actually entered the stubbed call before we assert on it, so this
        // doesn't race Mockito's own unnecessary-stubbing check at test teardown either.
        CountDownLatch reachedRoleLookup = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        when(guild.getRoleById(anyString())).thenAnswer(invocation -> {
            reachedRoleLookup.countDown();
            releaseWorker.await(5, TimeUnit.SECONDS);
            return null;
        });

        try {
            wipeService.start(guild, false, List.of(), "actor-1", "actor");
            assertThat(reachedRoleLookup.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> wipeService.start(guild, false, List.of(), "actor-1", "actor"))
                    .isInstanceOf(WipeInProgressException.class)
                    .hasMessage("Wipe already in progress for this guild");
        } finally {
            releaseWorker.countDown();
        }
    }
}
