package sk.gkanocz.aisauth.support;

import io.jsonwebtoken.Jwts;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.auth.AdminSession;
import sk.gkanocz.aisauth.auth.AdminSessionRepository;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import tools.jackson.core.type.TypeReference;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Issues a real JWT (signed with the fixed test key that {@link TestcontainersConfiguration}'s
 * JwtDecoder bean trusts - no live Keycloak needed) + a matching admin_sessions row, since
 * JwtAuthenticationFilter requires both (a valid signature AND a live session row) before it'll
 * authenticate a request. Lets integration tests exercise the real security filter chain instead
 * of mocking auth away.
 */
@Component
public class AuthenticatedRequestHelper {

    private static final String TEST_MANAGER_ROLE_ID = "test-manager-role";
    private static final String TEST_MANAGER_DISCORD_ID = "manager-1";

    @Autowired
    private AdminSessionRepository adminSessionRepository;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private DiscordBotService discordBotService;

    public String tokenFor(String discordId, boolean superAdmin, List<String> guildIds) {
        IssuedToken issued = rawIssue(discordId, "test_user", superAdmin, guildIds);
        adminSessionRepository.save(new AdminSession(issued.jti(), discordId, LocalDateTime.now().plusHours(1)));
        return issued.token();
    }

    /** Mints a token WITHOUT persisting an admin_sessions row - lets tests simulate a revoked/logged-out session. */
    public IssuedToken rawIssue(String discordId, String username, boolean superAdmin, List<String> guildIds) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .subject(discordId)
                .claim("discord_id", discordId)
                .claim("username", username)
                .claim("superAdmin", superAdmin)
                .claim("guildIds", guildIds)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(TestcontainersConfiguration.TEST_JWT_SIGNING_KEY)
                .compact();

        return new IssuedToken(token, jti, LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
    }

    public String superAdminToken() {
        return tokenFor("super-admin-1", true, List.of());
    }

    public String managerTokenFor(String guildId) {
        registerLiveManagerAccess(guildId);
        return tokenFor(TEST_MANAGER_DISCORD_ID, false, List.of(guildId));
    }

    public String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * GuildAccessService.canManageGuild checks live JDA member-cache state on every request
     * instead of the guildIds JWT claim (see its javadoc - a revoked Discord role must take effect
     * immediately, not just at the next token refresh). The test DiscordBotService bean is a
     * Mockito mock (TestcontainersConfiguration.discordBotService()), so a manager token only
     * passes the access check if this seeds matching "live" state: the guild is allowed, it has a
     * configured manager role, and the token's Discord id currently holds that role.
     */
    private void registerLiveManagerAccess(String guildId) {
        List<String> allowedGuildIds = new ArrayList<>(adminSettingsService.get(
                "allowed_guild_ids", new TypeReference<List<String>>() { }, List.of()));
        if (!allowedGuildIds.contains(guildId)) {
            allowedGuildIds.add(guildId);
            adminSettingsService.set("allowed_guild_ids", allowedGuildIds);
        }
        adminSettingsService.set(
                "dashboard_settings_" + guildId, new DashboardSettings(List.of(TEST_MANAGER_ROLE_ID)));

        Role role = mock(Role.class);
        lenient().when(role.getId()).thenReturn(TEST_MANAGER_ROLE_ID);
        Member member = mock(Member.class);
        lenient().when(member.getRoles()).thenReturn(List.of(role));
        Guild guild = mock(Guild.class);
        lenient().when(guild.getMemberById(TEST_MANAGER_DISCORD_ID)).thenReturn(member);

        JDA jda = discordBotService.jda().orElse(null);
        if (jda == null) {
            jda = mock(JDA.class);
            lenient().when(discordBotService.jda()).thenReturn(Optional.of(jda));
        }
        lenient().when(jda.getGuildById(guildId)).thenReturn(guild);
    }

    public record IssuedToken(String token, String jti, LocalDateTime expiresAt) {
    }
}
