package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.List;

public final class BotPermissionChecker {

    private BotPermissionChecker() {
    }

    public static List<String> missingPermissions(Guild guild, Permission... required) {
        Member self = guild.getSelfMember();
        List<String> missing = new ArrayList<>();
        for (Permission permission : required) {
            if (!self.hasPermission(permission)) {
                missing.add(permission.getName());
            }
        }
        return missing;
    }

    /**
     * Roles the bot cannot manage because they sit at or above the bot's own highest role.
     */
    public static List<String> rolesAboveBot(Guild guild, Role... roles) {
        Member self = guild.getSelfMember();
        List<String> tooHigh = new ArrayList<>();
        for (Role role : roles) {
            if (role != null && !self.canInteract(role)) {
                tooHigh.add(role.getName());
            }
        }
        return tooHigh;
    }
}
