package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import sk.gkanocz.aisauth.automod.AutoDeleteListener;
import sk.gkanocz.aisauth.automod.AutoMentionListener;
import sk.gkanocz.aisauth.automod.HackedAccountTrapListener;
import sk.gkanocz.aisauth.rolemenu.RoleMenuInteractionListener;
import sk.gkanocz.aisauth.verification.DatabaseSyncService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers jda()/requireGuild() (pure guard logic over the volatile jda field) and baseCommands()
 * (the static command catalog). run()/registerCommands()/updateAvatar() open a real Discord
 * gateway connection via JDABuilder and are not unit-testable - that's exercised live via the
 * docker-compose environment, same scoping call as other real-connection code this session.
 */
@ExtendWith(MockitoExtension.class)
class DiscordBotServiceTest {

    @Mock
    private JDA jda;
    @Mock
    private Guild guild;

    private DiscordBotService service;

    @BeforeEach
    void setUp() {
        service = new DiscordBotService(
                mock(DiscordBotProperties.class), mock(CommandInteractionListener.class),
                mock(GuildLifecycleListener.class), mock(AutoDeleteListener.class),
                mock(AutoMentionListener.class), mock(HackedAccountTrapListener.class),
                mock(RoleMenuInteractionListener.class),
                mock(VerifyConfirmationButtonListener.class), mock(GuildAllowlistEventManager.class),
                mock(DatabaseSyncService.class));
    }

    // ---- jda() / requireGuild() ----

    @Test
    void jdaIsEmptyBeforeTheBotConnects() {
        assertThat(service.jda()).isEmpty();
    }

    @Test
    void jdaIsPresentOnceConnected() {
        ReflectionTestUtils.setField(service, "jda", jda);

        assertThat(service.jda()).contains(jda);
    }

    @Test
    void requireGuildThrowsWhenBotNotConnected() {
        assertThatThrownBy(() -> service.requireGuild("guild-1"))
                .isInstanceOf(GuildNotAvailableException.class)
                .hasMessage("Discord bot is not connected.");
    }

    @Test
    void requireGuildThrowsWhenBotIsNotAMemberOfTheGuild() {
        ReflectionTestUtils.setField(service, "jda", jda);
        when(jda.getGuildById("guild-1")).thenReturn(null);

        assertThatThrownBy(() -> service.requireGuild("guild-1"))
                .isInstanceOf(GuildNotAvailableException.class)
                .hasMessage("Bot is not a member of guild guild-1.");
    }

    @Test
    void requireGuildReturnsTheGuildWhenFound() {
        ReflectionTestUtils.setField(service, "jda", jda);
        when(jda.getGuildById("guild-1")).thenReturn(guild);

        assertThat(service.requireGuild("guild-1")).isSameAs(guild);
    }

    // ---- baseCommands() ----

    @Test
    void baseCommandsIncludesEveryExpectedTopLevelCommand() {
        List<SlashCommandData> commands = service.baseCommands();

        assertThat(commands).extracting(SlashCommandData::getName)
                .containsExactly("verify", "code", "find", "manualverify", "warn", "mywarns", "info", "user");
    }

    @Test
    void warnCommandHasAllFourSubcommands() {
        SlashCommandData warn = service.baseCommands().stream()
                .filter(c -> c.getName().equals("warn")).findFirst().orElseThrow();

        assertThat(warn.getSubcommands()).extracting(SubcommandData::getName)
                .containsExactly("add", "remove", "clearall", "list");
    }

    /**
     * /manualverify bypasses the LDAP eligibility check entirely, /warn clearall/remove erase
     * moderation history, and /info + /user surface server configuration and another member's AIS
     * ID/email/warning history - all four must be locked to administrators at Discord
     * command-definition time, not left open-by-default pending a super-admin manually configuring
     * CommandPermissions.
     */
    @Test
    void manualVerifyWarnInfoAndUserAreAdminOnlyByDefault() {
        List<SlashCommandData> commands = service.baseCommands();

        assertThat(commands.stream()
                        .filter(c -> List.of("manualverify", "warn", "info", "user").contains(c.getName())))
                .allSatisfy(c -> assertThat(c.getDefaultPermissions().getPermissionsRaw())
                        .isEqualTo(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR).getPermissionsRaw()));
    }

    @Test
    void everydayCommandsRemainUsableByEveryone() {
        List<SlashCommandData> commands = service.baseCommands();

        assertThat(commands.stream()
                        .filter(c -> List.of("verify", "code", "find", "mywarns").contains(c.getName())))
                .allSatisfy(c -> assertThat(c.getDefaultPermissions().getPermissionsRaw())
                        .isEqualTo(DefaultMemberPermissions.ENABLED.getPermissionsRaw()));
    }
}
