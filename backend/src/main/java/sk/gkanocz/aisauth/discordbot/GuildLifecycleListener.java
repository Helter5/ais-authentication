package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.rolesnapshot.RoleSnapshotService;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.verification.VerificationService;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;

import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
class GuildLifecycleListener extends ListenerAdapter {

    private final VerificationService verificationService;
    private final AuditLogService auditLogService;
    private final GuildSettingsService guildSettingsService;
    private final VerifiedUserRepository verifiedUserRepository;
    private final EventLogEmbedSender eventLogEmbedSender;
    private final RoleSnapshotService roleSnapshotService;

    /** Bot added to a server (invited) - not gated on the allowed-guilds list, since that's exactly
     *  what determines whether it'll actually do anything here; this just records that it happened. */
    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        try {
            auditLogService.log(new AuditLogEntry(
                    "dashboard", "Bot added to server", guild.getId(), guild.getName(), null, null, null, null,
                    Map.of("memberCount", guild.getMemberCount())));
        } catch (Exception e) {
            log.error("Failed to log guild join for {}", guild.getId(), e);
        }
    }

    /** Bot removed from a server (kicked, or the server left/deleted) - the Guild object is still
     *  the last-cached snapshot, so name/memberCount are still readable here. */
    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        Guild guild = event.getGuild();
        try {
            auditLogService.log(new AuditLogEntry(
                    "dashboard", "Bot removed from server", guild.getId(), guild.getName(), null, null, null, null,
                    Map.of("memberCount", guild.getMemberCount())));
        } catch (Exception e) {
            log.error("Failed to log guild leave for {}", guild.getId(), e);
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        String guildId = event.getGuild().getId();
        String discordId = event.getUser().getId();
        String aisId = verifiedUserRepository.findByDiscordIdAndGuildId(discordId, guildId)
                .map(VerifiedUser::getAisId).orElse(null);

        // Kick/ban/leave all fire this same event - event.getMember() is still the cached member
        // (with their roles) at this point, since MemberCachePolicy.ALL keeps it populated right
        // up until removal. /refresh reads this snapshot back if they return within its retention.
        try {
            roleSnapshotService.snapshot(event.getGuild(), event.getMember());
        } catch (Exception e) {
            log.error("Failed to snapshot roles for departed member {}", discordId, e);
        }

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

        if (aisId != null) {
            eventLogEmbedSender.send(event.getGuild(), LogEventType.VERIFIED_USER_REMOVED,
                    EventLogEmbedSender.base(EventLogEmbedSender.DANGER, "Removed From Users Directory",
                                    "This member left the server, or was kicked/banned, and has been removed from the Users Directory.")
                            .addField("User", EventLogEmbedSender.userField(discordId, event.getUser().getName()), true)
                            .addField("AIS ID", aisId, true));
        }
    }

    /**
     * Flags the Verified role being assigned some other way (dragged on manually, another bot,
     * a role-sync integration) - /verify and /manualverify both create the verified_users row
     * *before* assigning the role, so a role-add with no matching row means it didn't happen
     * through either of those, which is worth a moderator's attention.
     */
    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        String guildId = event.getGuild().getId();
        String verifiedRoleId = guildSettingsService.getOrCreate(guildId).getVerifiedRoleId();
        if (verifiedRoleId == null) {
            return;
        }
        boolean gotVerifiedRole = event.getRoles().stream().map(Role::getId).anyMatch(verifiedRoleId::equals);
        if (!gotVerifiedRole) {
            return;
        }
        String discordId = event.getUser().getId();
        if (verifiedUserRepository.existsByDiscordIdAndGuildId(discordId, guildId)) {
            return;
        }

        resolveRoleChangeActor(event.getGuild(), discordId, actor ->
                eventLogEmbedSender.send(event.getGuild(), LogEventType.VERIFIED_ROLE_ADDED_WITHOUT_VERIFY,
                        EventLogEmbedSender.base(EventLogEmbedSender.WARNING, "Verified Role Added Outside /verify",
                                        "This member has no matching Users Directory record - the role was likely assigned manually rather than through /verify or /manualverify.")
                                .addField("User", EventLogEmbedSender.userField(discordId, event.getUser().getName()), true)
                                .addField("Role", "<@&" + verifiedRoleId + ">", true)
                                .addField("Assigned By", actor, true)));
    }

    /**
     * Discord's role-change events don't carry the actor - only the audit log does. Best-effort:
     * looks at the most recent MEMBER_ROLE_UPDATE entry for this target and falls back to
     * "Unknown" if the bot lacks View Audit Log or nothing matches (audit log entries can lag
     * slightly behind the gateway event that triggered this lookup).
     */
    private void resolveRoleChangeActor(Guild guild, String targetId, Consumer<String> callback) {
        guild.retrieveAuditLogs()
                .type(ActionType.MEMBER_ROLE_UPDATE)
                .limit(10)
                .queue(
                        logs -> callback.accept(logs.stream()
                                .filter(entry -> targetId.equals(entry.getTargetId()) && entry.getUser() != null)
                                .findFirst()
                                .map(entry -> "<@" + entry.getUser().getId() + ">")
                                .orElse("Unknown")),
                        failure -> callback.accept("Unknown"));
    }

    /**
     * Keeps the Users Directory in sync when the Verified role is removed while the member stays
     * in the guild (manual removal, another bot, a role-sync integration) - kick/ban/leave are
     * already covered by onGuildMemberRemove above, but those don't fire for a plain role removal.
     */
    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        String guildId = event.getGuild().getId();
        String verifiedRoleId = guildSettingsService.getOrCreate(guildId).getVerifiedRoleId();
        if (verifiedRoleId == null) {
            return;
        }
        boolean lostVerifiedRole = event.getRoles().stream().map(Role::getId).anyMatch(verifiedRoleId::equals);
        if (!lostVerifiedRole) {
            return;
        }
        String discordId = event.getUser().getId();
        String aisId = verifiedUserRepository.findByDiscordIdAndGuildId(discordId, guildId)
                .map(VerifiedUser::getAisId).orElse(null);
        if (aisId == null) {
            return;
        }

        try {
            verificationService.cleanupDepartedMember(discordId, guildId);
            auditLogService.log(new AuditLogEntry(
                    "verification", "Verified role removed - Users Directory record deleted",
                    guildId, event.getGuild().getName(),
                    null, null, discordId, event.getUser().getName(),
                    Map.of("trigger", "Verified role removed while still a member")));
        } catch (Exception e) {
            log.error("Failed to clean up Users Directory record for {} after Verified role removal", discordId, e);
        }

        resolveRoleChangeActor(event.getGuild(), discordId, actor ->
                eventLogEmbedSender.send(event.getGuild(), LogEventType.VERIFIED_USER_REMOVED,
                        EventLogEmbedSender.base(EventLogEmbedSender.DANGER, "Removed From Users Directory",
                                        "The Verified role was removed while this member is still in the server, and they have been removed from the Users Directory.")
                                .addField("User", EventLogEmbedSender.userField(discordId, event.getUser().getName()), true)
                                .addField("AIS ID", aisId, true)
                                .addField("Removed By", actor, true)));
    }
}
