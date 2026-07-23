package sk.gkanocz.aisauth.discordbot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandPermissionsTest {

    @Test
    void emptyPermissionsAllowEverythingAndAreNotConfigured() {
        CommandPermissions permissions = CommandPermissions.empty();

        assertThat(permissions.isConfigured()).isFalse();
        assertThat(permissions.blockReason("chan-1", List.of("role-1"), false)).isNull();
    }

    @Test
    void ignoredChannelBlocksRegardlessOfOtherRules() {
        CommandPermissions permissions = new CommandPermissions(
                List.of("chan-1"), List.of("chan-1"), List.of(), List.of(), false);

        assertThat(permissions.blockReason("chan-1", List.of(), false)).isEqualTo("Channel is ignored");
    }

    @Test
    void allowedChannelsListBlocksAnyChannelNotOnIt() {
        CommandPermissions permissions = new CommandPermissions(
                List.of("chan-1"), List.of(), List.of(), List.of(), false);

        assertThat(permissions.blockReason("chan-2", List.of(), false)).isEqualTo("Channel is not allowed");
        assertThat(permissions.blockReason("chan-1", List.of(), false)).isNull();
    }

    @Test
    void ignoredRoleBlocksIfMemberHasIt() {
        CommandPermissions permissions = new CommandPermissions(
                List.of(), List.of(), List.of(), List.of("role-ignored"), false);

        assertThat(permissions.blockReason("chan-1", List.of("role-ignored"), false))
                .isEqualTo("User has an ignored role");
        assertThat(permissions.blockReason("chan-1", List.of("role-other"), false)).isNull();
    }

    @Test
    void allowedRolesListBlocksMembersWithoutAnyOfThem() {
        CommandPermissions permissions = new CommandPermissions(
                List.of(), List.of(), List.of("role-a", "role-b"), List.of(), false);

        assertThat(permissions.blockReason("chan-1", List.of("role-c"), false))
                .isEqualTo("User lacks an allowed role");
        assertThat(permissions.blockReason("chan-1", List.of("role-b"), false)).isNull();
    }

    @Test
    void adminOnlyBlocksNonAdministrators() {
        CommandPermissions permissions = new CommandPermissions(List.of(), List.of(), List.of(), List.of(), true);

        assertThat(permissions.blockReason("chan-1", List.of(), false)).isEqualTo("Admin only command");
        assertThat(permissions.blockReason("chan-1", List.of(), true)).isNull();
    }

    @Test
    void isConfiguredTrueWhenAnyRuleIsSet() {
        assertThat(new CommandPermissions(List.of("c"), List.of(), List.of(), List.of(), false).isConfigured()).isTrue();
        assertThat(new CommandPermissions(List.of(), List.of(), List.of(), List.of(), true).isConfigured()).isTrue();
    }
}
