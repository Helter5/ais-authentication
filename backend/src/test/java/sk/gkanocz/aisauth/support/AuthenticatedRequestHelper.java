package sk.gkanocz.aisauth.support;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.auth.AdminSession;
import sk.gkanocz.aisauth.auth.AdminSessionRepository;
import sk.gkanocz.aisauth.auth.JwtService;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Issues a real access token via the same {@link JwtService} production code uses (so it's signed
 * with whatever app.jwt.secret the test context resolves - no separate test-only key to keep in
 * sync) + a matching admin_sessions row, since JwtAuthenticationFilter requires both (a valid
 * signature AND a live session row) before it'll authenticate a request. Lets integration tests
 * exercise the real security filter chain instead of mocking auth away.
 */
@Component
public class AuthenticatedRequestHelper {

    private static final String TEST_MANAGER_ROLE_ID = "test-manager-role";
    private static final String TEST_MANAGER_DISCORD_ID = "manager-1";

    /**
     * GuildAccessService.isSuperAdmin now re-checks this ID against the live
     * app.admin.super-admin-ids config on every call (not just the JWT claim - see its javadoc),
     * so any test using superAdminToken() must also seed this ID into that config via
     * {@code @TestPropertySource(properties = "app.admin.super-admin-ids=" + SUPER_ADMIN_DISCORD_ID)}
     * on the test class, or every super-admin-gated assertion will 403.
     */
    public static final String SUPER_ADMIN_DISCORD_ID = "super-admin-1";

    @Autowired
    private AdminSessionRepository adminSessionRepository;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private DiscordBotService discordBotService;
    @Autowired
    private JwtService jwtService;

    public String tokenFor(String discordId, boolean superAdmin, List<String> guildIds) {
        IssuedToken issued = rawIssue(discordId, "test_user", superAdmin, guildIds);
        adminSessionRepository.save(new AdminSession(issued.jti(), discordId, LocalDateTime.now().plusHours(1)));
        return issued.token();
    }

    /** Mints a token WITHOUT persisting an admin_sessions row - lets tests simulate a revoked/logged-out session. */
    public IssuedToken rawIssue(String discordId, String username, boolean superAdmin, List<String> guildIds) {
        JwtService.IssuedAccessToken issued = jwtService.mintAccessToken(discordId, username, null, superAdmin, guildIds);
        return new IssuedToken(issued.token(), issued.jti(), issued.expiresAt());
    }

    public String superAdminToken() {
        return tokenFor(SUPER_ADMIN_DISCORD_ID, true, List.of());
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
