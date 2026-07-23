package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GuildAccessService {

    public boolean isSuperAdmin(Claims claims) {
        return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> guildIds(Claims claims) {
        List<String> guildIds = claims.get("guildIds", List.class);
        return guildIds == null ? List.of() : guildIds;
    }

    public boolean canManageGuild(Claims claims, String guildId) {
        return isSuperAdmin(claims) || guildIds(claims).contains(guildId);
    }

    public void assertSuperAdmin(Claims claims) {
        if (!isSuperAdmin(claims)) {
            throw GuildAccessDeniedException.superAdminRequired();
        }
    }

    public void assertCanManageGuild(Claims claims, String guildId) {
        if (!canManageGuild(claims, guildId)) {
            throw GuildAccessDeniedException.managerAccessRequired();
        }
    }
}
