package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/**
 * Resolves a Discord username from a cached guild member lookup (no REST call) - shared by every
 * admin-facing response DTO that shows both a raw Discord ID and, when available, its username.
 */
public final class DiscordNames {

    private DiscordNames() {
    }

    public static String memberName(Guild guild, String discordId) {
        if (guild == null || discordId == null) {
            return null;
        }
        Member member = guild.getMemberById(discordId);
        return member == null ? null : member.getUser().getName();
    }
}
