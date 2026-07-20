package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.verification.VerificationService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
class GuildLifecycleListener extends ListenerAdapter {

    private final VerificationService verificationService;
    private final AuditLogService auditLogService;

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        String guildId = event.getGuild().getId();
        String discordId = event.getUser().getId();

        try {
            verificationService.cleanupDepartedMember(discordId, guildId);
            auditLogService.log(new AuditLogEntry(
                    "verification", "Departed member cleanup",
                    guildId, event.getGuild().getName(),
                    null, null, discordId, event.getUser().getName(),
                    Map.of("trigger", "User left the server")));
        } catch (Exception e) {
            log.error("Failed to clean up departed member {}", discordId, e);
        }
    }
}
