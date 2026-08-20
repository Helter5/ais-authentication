package sk.gkanocz.aisauth.rolesnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Backs the {@code /refresh} command: remembers the trackable roles a member had when they
 * left/were kicked/were banned, so they can get them back if they return within
 * {@link #RETENTION_DAYS}. Two independent filters decide what "trackable" means:
 *
 * <p>1. {@link #ELEVATED_PERMISSIONS} - a hardcoded floor, never overridable from the dashboard.
 * Any role granting one of these is never snapshotted and never restored, full stop. This is the
 * actual security boundary: even if a guild's exclude-list is misconfigured or empty, a
 * compromised/recreated account can never claw back admin/mod privileges through this command.
 *
 * <p>2. The guild's {@code cmd_settings_<guildId>_refresh.excludeRoleIds} dashboard list - an
 * additional, admin-editable exclusion on top of the floor above (event roles, muted role, a
 * staff role that happens not to carry an elevated Discord permission, etc).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleSnapshotService {

    /** Mirrors the old bot's retention window for departed-member role memory. */
    static final int RETENTION_DAYS = 30;

    private static final Set<Permission> ELEVATED_PERMISSIONS = Set.of(
            Permission.ADMINISTRATOR, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER,
            Permission.BAN_MEMBERS, Permission.KICK_MEMBERS, Permission.MANAGE_CHANNEL);

    public record RefreshOutcome(boolean hadSnapshot, List<Role> rolesToApply) {
        static RefreshOutcome noSnapshot() {
            return new RefreshOutcome(false, List.of());
        }
    }

    private final RoleSnapshotRepository roleSnapshotRepository;
    private final AdminSettingsService adminSettingsService;
    private final ObjectMapper objectMapper;

    /**
     * Called on member departure (leave/kick/ban all fire GuildMemberRemoveEvent). Always reflects
     * this, the *most recent*, departure - not an earlier one. Concretely: leave (roles X saved) ->
     * rejoin without ever running /refresh -> pick up a different role set Y -> leave again must
     * end up remembering Y, not the stale X, even when Y is empty. That's why an empty trackable
     * set still touches an existing row (deletes it) instead of silently leaving X in place - the
     * old "just return early" version left exactly that stale-X bug behind. Only a genuinely new
     * departure with nothing trackable AND no prior row is a true no-op (nothing to create).
     */
    @Transactional
    public void snapshot(Guild guild, Member member) {
        if (member == null) {
            return;
        }
        Set<String> excludeRoleIds = excludeRoleIds(guild.getId());
        List<String> trackableRoleIds = member.getRoles().stream()
                .filter(role -> !isElevated(role))
                .filter(role -> !excludeRoleIds.contains(role.getId()))
                .map(Role::getId)
                .toList();

        String guildId = guild.getId();
        String discordId = member.getId();
        Optional<RoleSnapshot> existing = roleSnapshotRepository.findByGuildIdAndDiscordId(guildId, discordId);

        if (trackableRoleIds.isEmpty()) {
            // This departure has nothing worth remembering - an existing row is now stale
            // (it describes an earlier, no-longer-current departure) and must not survive it.
            existing.ifPresent(roleSnapshotRepository::delete);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(RETENTION_DAYS);
        String json = objectMapper.writeValueAsString(trackableRoleIds);

        existing.ifPresentOrElse(
                snapshot -> snapshot.overwrite(json, now, expiresAt),
                () -> roleSnapshotRepository.save(new RoleSnapshot(guildId, discordId, json, now, expiresAt)));
    }

    /**
     * Called from {@code /refresh} to see what would be restored - does NOT consume the snapshot;
     * the caller only does that (via {@link #consumeSnapshot}) once the roles were actually,
     * successfully re-assigned, so a JDA failure (missing permission, hierarchy) leaves the
     * snapshot intact for a retry instead of burning the member's one shot at it. The one
     * exception: a snapshot found already past its retention window is deleted right here - it's
     * dead weight either way, and there is nothing to "retry" about an expired snapshot. Roles
     * that no longer exist, are newly elevated, or are newly excluded since the snapshot was
     * taken are silently dropped from the result rather than failing the whole command.
     */
    @Transactional
    public RefreshOutcome refresh(Guild guild, Member member) {
        Optional<RoleSnapshot> snapshotOpt = roleSnapshotRepository.findByGuildIdAndDiscordId(guild.getId(), member.getId());
        if (snapshotOpt.isEmpty()) {
            return RefreshOutcome.noSnapshot();
        }
        RoleSnapshot snapshot = snapshotOpt.get();
        if (snapshot.getExpiresAt().isBefore(LocalDateTime.now())) {
            roleSnapshotRepository.delete(snapshot);
            return RefreshOutcome.noSnapshot();
        }

        Set<String> excludeRoleIds = excludeRoleIds(guild.getId());
        List<String> savedRoleIds = objectMapper.readValue(snapshot.getRoleIds(), new TypeReference<List<String>>() { });

        List<Role> rolesToApply = savedRoleIds.stream()
                .map(guild::getRoleById)
                .filter(Objects::nonNull)
                .filter(role -> !isElevated(role))
                .filter(role -> !excludeRoleIds.contains(role.getId()))
                .toList();

        return new RefreshOutcome(true, rolesToApply);
    }

    /** Deletes the snapshot - call only after the roles from {@link #refresh} were actually applied. */
    @Transactional
    public void consumeSnapshot(String guildId, String discordId) {
        roleSnapshotRepository.deleteByGuildIdAndDiscordId(guildId, discordId);
    }

    private boolean isElevated(Role role) {
        return ELEVATED_PERMISSIONS.stream().anyMatch(permission -> role.hasPermission(permission));
    }

    private Set<String> excludeRoleIds(String guildId) {
        Map<String, Object> settings = adminSettingsService.get(
                "cmd_settings_" + guildId + "_refresh", new TypeReference<Map<String, Object>>() { }, Map.of());
        Object raw = settings.get("excludeRoleIds");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }
}
