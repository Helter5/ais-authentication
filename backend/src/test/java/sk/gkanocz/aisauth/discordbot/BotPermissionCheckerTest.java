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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotPermissionCheckerTest {

    @Mock
    private Guild guild;
    @Mock
    private Member self;

    @BeforeEach
    void setUp() {
        when(guild.getSelfMember()).thenReturn(self);
    }

    // ---- missingPermissions ----

    @Test
    void missingPermissionsReturnsEmptyWhenBotHasEverything() {
        when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(self.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true);

        assertThat(BotPermissionChecker.missingPermissions(guild, Permission.MANAGE_ROLES, Permission.MANAGE_CHANNEL)).isEmpty();
    }

    @Test
    void missingPermissionsListsOnlyThePermissionsTheBotLacks() {
        when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(self.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false);

        assertThat(BotPermissionChecker.missingPermissions(guild, Permission.MANAGE_ROLES, Permission.MANAGE_CHANNEL))
                .containsExactly(Permission.MANAGE_CHANNEL.getName());
    }

    @Test
    void missingPermissionsReturnsEmptyForNoRequiredPermissions() {
        assertThat(BotPermissionChecker.missingPermissions(guild)).isEmpty();
    }

    // ---- rolesAboveBot ----

    @Test
    void rolesAboveBotReturnsEmptyWhenBotCanInteractWithAllRoles() {
        Role role = mock(Role.class);
        when(self.canInteract(role)).thenReturn(true);

        assertThat(BotPermissionChecker.rolesAboveBot(guild, role)).isEmpty();
    }

    @Test
    void rolesAboveBotListsRolesTheBotCannotInteractWith() {
        Role tooHigh = mock(Role.class);
        when(tooHigh.getName()).thenReturn("Admin");
        when(self.canInteract(tooHigh)).thenReturn(false);

        assertThat(BotPermissionChecker.rolesAboveBot(guild, tooHigh)).containsExactly("Admin");
    }

    @Test
    void rolesAboveBotSkipsNullRoles() {
        assertThat(BotPermissionChecker.rolesAboveBot(guild, (Role) null)).isEmpty();
    }
}
