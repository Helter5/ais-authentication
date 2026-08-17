package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAuditLoggerTest {

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private Claims claims;
    @Mock
    private JDA jda;
    @Mock
    private Guild guild;

    private DashboardAuditLogger logger;

    @BeforeEach
    void setUp() {
        logger = new DashboardAuditLogger(auditLogService, discordBotService);
    }

    private AuditLogEntry captureEntry() {
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        return captor.getValue();
    }

    @Test
    void logsFromClaimsAndGuildIdResolvingGuildNameViaJda() {
        when(claims.getSubject()).thenReturn("mod-1");
        when(claims.get("username", String.class)).thenReturn("ModName");
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById("guild-1")).thenReturn(guild);
        when(guild.getName()).thenReturn("My Guild");

        logger.log(claims, "guild-1", "did a thing", Map.of("k", "v"));

        AuditLogEntry entry = captureEntry();
        assertThat(entry.category()).isEqualTo("dashboard");
        assertThat(entry.action()).isEqualTo("did a thing");
        assertThat(entry.guildId()).isEqualTo("guild-1");
        assertThat(entry.guildName()).isEqualTo("My Guild");
        assertThat(entry.userId()).isEqualTo("mod-1");
        assertThat(entry.username()).isEqualTo("ModName");
        assertThat(entry.details()).isEqualTo(Map.of("k", "v"));
    }

    @Test
    void logsFromClaimsAndGuildIdWithNullGuildNameWhenJdaUnavailable() {
        when(claims.getSubject()).thenReturn("mod-1");
        when(claims.get("username", String.class)).thenReturn("ModName");
        when(discordBotService.jda()).thenReturn(Optional.empty());

        logger.log(claims, "guild-1", "did a thing", Map.of());

        assertThat(captureEntry().guildName()).isNull();
    }

    @Test
    void logsFromClaimsAndGuildIdWithNullGuildNameWhenGuildNotFound() {
        when(claims.getSubject()).thenReturn("mod-1");
        when(claims.get("username", String.class)).thenReturn("ModName");
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById("guild-1")).thenReturn(null);

        logger.log(claims, "guild-1", "did a thing", Map.of());

        assertThat(captureEntry().guildName()).isNull();
    }

    @Test
    void logsFromClaimsAndGuildDirectly() {
        when(claims.getSubject()).thenReturn("mod-1");
        when(claims.get("username", String.class)).thenReturn("ModName");
        when(guild.getId()).thenReturn("guild-1");
        when(guild.getName()).thenReturn("My Guild");

        logger.log(claims, guild, "did a thing", Map.of());

        AuditLogEntry entry = captureEntry();
        assertThat(entry.guildId()).isEqualTo("guild-1");
        assertThat(entry.guildName()).isEqualTo("My Guild");
    }

    @Test
    void logsFromRawActorAndGuildId() {
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById("guild-1")).thenReturn(guild);
        when(guild.getName()).thenReturn("My Guild");

        logger.log("mod-1", "ModName", "guild-1", "did a thing", Map.of());

        AuditLogEntry entry = captureEntry();
        assertThat(entry.userId()).isEqualTo("mod-1");
        assertThat(entry.username()).isEqualTo("ModName");
        assertThat(entry.guildName()).isEqualTo("My Guild");
    }

    @Test
    void logsFromRawActorAndGuildDirectly() {
        when(guild.getId()).thenReturn("guild-1");
        when(guild.getName()).thenReturn("My Guild");

        logger.log("mod-1", "ModName", guild, "did a thing", Map.of());

        AuditLogEntry entry = captureEntry();
        assertThat(entry.userId()).isEqualTo("mod-1");
        assertThat(entry.guildId()).isEqualTo("guild-1");
    }

    @Test
    void swallowsAuditLogFailure() {
        when(guild.getId()).thenReturn("guild-1");
        when(guild.getName()).thenReturn("My Guild");
        doThrow(new RuntimeException("db down")).when(auditLogService).log(any());

        assertThatCode(() -> logger.log("mod-1", "ModName", guild, "did a thing", Map.of()))
                .doesNotThrowAnyException();
    }
}
