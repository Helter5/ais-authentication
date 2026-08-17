package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifiedRoleResolverTest {

    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private Guild guild;
    @Mock
    private Member botMember;
    @Mock
    private Role role;

    private VerifiedRoleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new VerifiedRoleResolver(guildSettingsService);
        lenient().when(guild.getId()).thenReturn("guild-1");
    }

    private GuildSettings settingsWithRole(String roleId) {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId(roleId);
        return settings;
    }

    @Test
    void throwsWhenNoRoleConfigured() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(new GuildSettings("guild-1"));

        assertThatThrownBy(() -> resolver.resolveAssignable(guild))
                .isInstanceOf(VerifiedRoleUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void throwsWhenConfiguredRoleNoLongerExists() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithRole("role-1"));
        when(guild.getRoleById("role-1")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveAssignable(guild))
                .isInstanceOf(VerifiedRoleUnavailableException.class)
                .hasMessageContaining("no longer exists");
    }

    @Test
    void throwsWhenBotIsMissingManageRolesPermission() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithRole("role-1"));
        when(guild.getRoleById("role-1")).thenReturn(role);
        when(guild.getSelfMember()).thenReturn(botMember);
        when(botMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false);

        assertThatThrownBy(() -> resolver.resolveAssignable(guild))
                .isInstanceOf(VerifiedRoleUnavailableException.class)
                .hasMessageContaining("Manage Roles");
    }

    @Test
    void throwsWhenRoleIsManagedByAnIntegration() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithRole("role-1"));
        when(guild.getRoleById("role-1")).thenReturn(role);
        when(guild.getSelfMember()).thenReturn(botMember);
        when(botMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(role.isManaged()).thenReturn(true);
        when(role.getName()).thenReturn("Bot Role");

        assertThatThrownBy(() -> resolver.resolveAssignable(guild))
                .isInstanceOf(VerifiedRoleUnavailableException.class)
                .hasMessageContaining("managed by an integration");
    }

    @Test
    void throwsWhenBotRoleIsBelowTheConfiguredRoleInHierarchy() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithRole("role-1"));
        when(guild.getRoleById("role-1")).thenReturn(role);
        when(guild.getSelfMember()).thenReturn(botMember);
        when(botMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(role.isManaged()).thenReturn(false);
        when(botMember.canInteract(role)).thenReturn(false);
        when(role.getName()).thenReturn("Verified");

        assertThatThrownBy(() -> resolver.resolveAssignable(guild))
                .isInstanceOf(VerifiedRoleUnavailableException.class)
                .hasMessageContaining("Move the bot role above");
    }

    @Test
    void returnsTheRoleWhenEverythingChecksOut() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithRole("role-1"));
        when(guild.getRoleById("role-1")).thenReturn(role);
        when(guild.getSelfMember()).thenReturn(botMember);
        when(botMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(role.isManaged()).thenReturn(false);
        when(botMember.canInteract(role)).thenReturn(true);

        assertThat(resolver.resolveAssignable(guild)).isSameAs(role);
    }
}
