package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.settings.AdminSettingRepository;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers toGuildCommandData()'s permission-floor logic - the fix for a regression that would have
 * silently undone DiscordBotService.baseCommands()' admin-only lockout on /manualverify and /warn:
 * "Sync Visibility" rebuilds each command's SlashCommandData from scratch, so unless it explicitly
 * carries forward a base command's own DefaultMemberPermissions, a super-admin who never touched
 * Authorization for those two commands would reopen them to every member on the next sync.
 */
@ExtendWith(MockitoExtension.class)
class CommandManagementControllerTest {

    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private AdminSettingRepository adminSettingRepository;
    @Mock
    private GuildAccessService guildAccessService;
    @Mock
    private DashboardAuditLogger dashboardAuditLogger;
    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private Claims claims;
    @Mock
    private Guild guild;

    private CommandManagementController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        controller = new CommandManagementController(
                adminSettingsService, adminSettingRepository, guildAccessService, dashboardAuditLogger,
                discordBotService, new ObjectMapper());

        Mockito.lenient().when(adminSettingsService.get(anyString(), eq(CommandPermissions.class), any()))
                .thenReturn(CommandPermissions.empty());
        Mockito.lenient().when(adminSettingsService.get(anyString(), any(TypeReference.class), any()))
                .thenReturn(Map.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deployCommandsNeverWeakensACommandsBaseAdminOnlyDefault() {
        String guildId = "guild-1";
        SlashCommandData manualverify = Commands.slash("manualverify", "desc")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
        SlashCommandData verify = Commands.slash("verify", "desc");
        when(discordBotService.baseCommands()).thenReturn(List.of(manualverify, verify));
        when(discordBotService.requireGuild(guildId)).thenReturn(guild);
        CommandListUpdateAction action = mock(CommandListUpdateAction.class, Mockito.RETURNS_SELF);
        when(guild.updateCommands()).thenReturn(action);

        controller.deployCommands(claims, new CommandManagementController.GuildIdRequest(guildId));

        ArgumentCaptor<Collection<CommandData>> captor = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(action).addCommands(captor.capture());
        List<CommandData> deployed = List.copyOf(captor.getValue());

        CommandData deployedManualVerify = deployed.stream()
                .filter(c -> c.getName().equals("manualverify")).findFirst().orElseThrow();
        CommandData deployedVerify = deployed.stream()
                .filter(c -> c.getName().equals("verify")).findFirst().orElseThrow();

        assertThat(deployedManualVerify.getDefaultPermissions().getPermissionsRaw())
                .isEqualTo(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR).getPermissionsRaw());
        assertThat(deployedVerify.getDefaultPermissions().getPermissionsRaw())
                .isEqualTo(DefaultMemberPermissions.ENABLED.getPermissionsRaw());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deployCommandsStillLocksDownAnExplicitlyConfiguredAdminOnlyCommand() {
        String guildId = "guild-1";
        SlashCommandData find = Commands.slash("find", "desc");
        when(discordBotService.baseCommands()).thenReturn(List.of(find));
        when(discordBotService.requireGuild(guildId)).thenReturn(guild);
        when(adminSettingsService.get(
                eq("cmd_perms_" + guildId + "_find"), eq(CommandPermissions.class), any()))
                .thenReturn(new CommandPermissions(List.of(), List.of(), List.of(), List.of(), true));
        CommandListUpdateAction action = mock(CommandListUpdateAction.class, Mockito.RETURNS_SELF);
        when(guild.updateCommands()).thenReturn(action);

        controller.deployCommands(claims, new CommandManagementController.GuildIdRequest(guildId));

        ArgumentCaptor<Collection<CommandData>> captor = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(action).addCommands(captor.capture());
        CommandData deployedFind = List.copyOf(captor.getValue()).get(0);

        assertThat(deployedFind.getDefaultPermissions().getPermissionsRaw())
                .isEqualTo(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR).getPermissionsRaw());
    }
}
