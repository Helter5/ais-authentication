package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.GuildSettingsService;

/**
 * Java port of the old bot's resolveAssignableVerifiedRole (utils/VerificationRole.js) - refuses to
 * even start a verification (send a code, confirm one, or manually verify) unless the bot can
 * actually assign the configured role right now. Without this, /verify would happily email a code
 * for a role the bot could never grant, and /code would report success while silently failing to
 * assign anything.
 */
@Component
@RequiredArgsConstructor
public class VerifiedRoleResolver {

    private final GuildSettingsService guildSettingsService;

    public Role resolveAssignable(Guild guild) {
        String roleId = guildSettingsService.getOrCreate(guild.getId()).getVerifiedRoleId();
        if (roleId == null) {
            throw VerifiedRoleUnavailableException.notConfigured();
        }

        Role role = guild.getRoleById(roleId);
        if (role == null) {
            throw VerifiedRoleUnavailableException.roleDeleted();
        }

        Member botMember = guild.getSelfMember();
        if (!botMember.hasPermission(Permission.MANAGE_ROLES)) {
            throw VerifiedRoleUnavailableException.missingManageRoles();
        }
        if (role.isManaged()) {
            throw VerifiedRoleUnavailableException.managedRole(role.getName());
        }
        if (!botMember.canInteract(role)) {
            throw VerifiedRoleUnavailableException.belowBotRole(role.getName());
        }

        return role;
    }
}
