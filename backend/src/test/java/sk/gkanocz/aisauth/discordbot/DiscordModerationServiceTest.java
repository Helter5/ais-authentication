package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscordModerationServiceTest {

    @Mock
    private Member target;
    @Mock
    private Member self;
    @Mock
    private Guild guild;

    private DiscordModerationService service;

    @BeforeEach
    void setUp() {
        service = new DiscordModerationService();
        Mockito.lenient().when(target.getGuild()).thenReturn(guild);
        Mockito.lenient().when(guild.getSelfMember()).thenReturn(self);
    }

    // ---- missingPermission ----

    @Test
    void missingPermissionReturnsNullForAnUnrecognizedAction() {
        assertThat(service.missingPermission(target, "mute")).isNull();
    }

    @Test
    void missingPermissionReturnsNullWhenBotCanActOnTheTarget() {
        when(self.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true);
        when(self.canInteract(target)).thenReturn(true);

        assertThat(service.missingPermission(target, "ban")).isNull();
    }

    @Test
    void missingPermissionReportsWhenBotLacksTheRequiredPermission() {
        when(self.hasPermission(Permission.BAN_MEMBERS)).thenReturn(false);

        assertThat(service.missingPermission(target, "ban")).contains("BAN_MEMBERS");
    }

    @Test
    void missingPermissionReportsHierarchyWhenTargetOutranksTheBot() {
        when(self.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true);
        when(self.canInteract(target)).thenReturn(false);

        assertThat(service.missingPermission(target, "kick")).contains("target's role is equal to or higher");
    }

    @Test
    void missingPermissionChecksModerateMembersForTimeout() {
        when(self.hasPermission(Permission.MODERATE_MEMBERS)).thenReturn(true);
        when(self.canInteract(target)).thenReturn(true);

        assertThat(service.missingPermission(target, "timeout")).isNull();
    }

    // ---- apply ----

    @Test
    void applyReturnsNullForAnUnrecognizedAction() {
        assertThat(service.apply(target, "mute", "reason", null)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyBanSucceeds() {
        AuditableRestAction<Void> action = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(target.ban(0, TimeUnit.SECONDS)).thenReturn(action);

        DiscordModerationService.Outcome outcome = service.apply(target, "ban", "spam", null);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.detail()).isEqualTo("banned");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyKickSucceeds() {
        AuditableRestAction<Void> action = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(target.kick()).thenReturn(action);

        DiscordModerationService.Outcome outcome = service.apply(target, "kick", "spam", null);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.detail()).isEqualTo("kicked");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyTimeoutSucceedsAndFormatsMinutes() {
        AuditableRestAction<Void> action = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(target.timeoutFor(Duration.ofMinutes(45))).thenReturn(action);

        DiscordModerationService.Outcome outcome = service.apply(target, "timeout", "spam", Duration.ofMinutes(45));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.detail()).isEqualTo("timed out (45m)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyTimeoutFormatsWholeHours() {
        AuditableRestAction<Void> action = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(target.timeoutFor(Duration.ofHours(2))).thenReturn(action);

        DiscordModerationService.Outcome outcome = service.apply(target, "timeout", "spam", Duration.ofHours(2));

        assertThat(outcome.detail()).isEqualTo("timed out (2h)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyReturnsFailureOutcomeWhenActionThrows() {
        AuditableRestAction<Void> action = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(target.ban(0, TimeUnit.SECONDS)).thenReturn(action);
        when(action.complete()).thenThrow(new HierarchyException("nope"));

        DiscordModerationService.Outcome outcome = service.apply(target, "ban", "spam", null);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.detail()).contains("target's role is equal to or higher");
    }

    // ---- describeFailure ----

    @Test
    void describeFailureExplainsInsufficientPermission() {
        InsufficientPermissionException e = new InsufficientPermissionException(guild, Permission.BAN_MEMBERS);

        assertThat(service.describeFailure(e)).contains("BAN_MEMBERS");
    }

    @Test
    void describeFailureExplainsHierarchyException() {
        assertThat(service.describeFailure(new HierarchyException("nope"))).isEqualTo("target's role is equal to or higher than the bot's role");
    }

    @Test
    void describeFailureFallsBackToTheExceptionMessage() {
        assertThat(service.describeFailure(new RuntimeException("boom"))).isEqualTo("boom");
    }

    @Test
    void describeFailureFallsBackToTheClassNameWhenMessageIsNull() {
        assertThat(service.describeFailure(new RuntimeException())).isEqualTo("RuntimeException");
    }
}
