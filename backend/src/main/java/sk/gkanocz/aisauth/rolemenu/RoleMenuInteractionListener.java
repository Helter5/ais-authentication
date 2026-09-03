package sk.gkanocz.aisauth.rolemenu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.discordbot.BotPermissionChecker;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.verification.MemberVerificationChecker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Handles clicks on a role menu's buttons/select-menu (posted by RoleMenuService). Not gated
 * through CommandInteractionListener since these are component interactions, not slash commands -
 * the guild allowlist check happens one layer up in GuildAllowlistEventManager instead.
 *
 * <p>Each {@link RoleMenuOption} may bundle several Discord roles under one button/select entry
 * (e.g. "2. ROCNIK + API" granting two roles at once) - {@link #hasOption} treats "has this
 * option" as holding every one of its (still-existing) roles, and {@link #applyOption} adds/
 * removes whichever of the bundle's roles the member is missing/holding, skipping roles that no
 * longer exist on the server rather than failing the whole bundle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleMenuInteractionListener extends ListenerAdapter {

    private static final String PREFIX = "rolemenu:";

    private final RoleMenuConfigRepository roleMenuConfigRepository;
    private final RoleMenuService roleMenuService;
    private final AdminSettingsService adminSettingsService;
    private final MemberVerificationChecker memberVerificationChecker;
    private final AuditLogService auditLogService;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(PREFIX)) {
            return;
        }
        String[] parts = id.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            return;
        }
        Long configId = parseId(parts[0]);
        Long optionIndexRaw = parseId(parts[1]);
        if (configId == null || optionIndexRaw == null) {
            return;
        }
        if (event.getGuild() == null) {
            return;
        }
        int optionIndex = optionIndexRaw.intValue();

        // Ack first: the config lookup + verification check below hit the DB, and during a Postgres
        // outage that blocks ~10s on the dead connection pool - long past Discord's 3s deadline.
        event.deferReply(true).queue();
        event.getHook().setEphemeral(true); // hook followups don't inherit deferReply's flag on their own
        withDbGuard(event, () -> withConfig(event, configId, config -> {
            String denied = accessDeniedReason(event.getGuild(), event.getMember(), config);
            if (denied != null) {
                event.getHook().sendMessage(denied).queue();
                return;
            }

            Guild guild = event.getGuild();
            Member member = event.getMember();
            List<RoleMenuOption> options = roleMenuService.readOptions(config.getOptions());
            if (optionIndex < 0 || optionIndex >= options.size()) {
                event.getHook().sendMessage("That role isn't part of this menu anymore.").queue();
                return;
            }
            RoleMenuOption clicked = options.get(optionIndex);

            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            List<String> blocked = new ArrayList<>();
            boolean alreadyHas = hasOption(guild, member, clicked);
            String messageType = config.getMessageType();

            switch (messageType) {
                case "UNIQUE" -> {
                    // Always grants the clicked option rather than toggling it off - a bundle can
                    // share a role with a sibling option (e.g. "2. ROCNIK + API" vs "API"), so
                    // "alreadyHas" is ambiguous about which option the member actually picked.
                    // Roles the clicked option itself wants are protected from the sibling-clear
                    // pass below, so an overlapping role never gets removed then re-added.
                    Set<String> keep = new HashSet<>(clicked.roleIds());
                    for (int i = 0; i < options.size(); i++) {
                        if (i != optionIndex) {
                            applyOption(guild, member, options.get(i), false, keep, added, removed, blocked);
                        }
                    }
                    applyOption(guild, member, clicked, true, Set.of(), added, removed, blocked);
                }
                case "VERIFY" -> {
                    if (alreadyHas) {
                        event.getHook().sendMessage("You already have this role.").queue();
                        return;
                    }
                    if (config.getMaxSelectable() != null && countHeld(guild, member, options) >= config.getMaxSelectable()) {
                        event.getHook().sendMessage("You can only have up to " + config.getMaxSelectable()
                                + " role(s) from this menu.").queue();
                        return;
                    }
                    applyOption(guild, member, clicked, true, Set.of(), added, removed, blocked);
                }
                case "DROP" -> {
                    if (!alreadyHas) {
                        event.getHook().sendMessage("You don't have this role.").queue();
                        return;
                    }
                    applyOption(guild, member, clicked, false, Set.of(), added, removed, blocked);
                }
                case "BINDING" -> {
                    if (options.stream().anyMatch(o -> hasOption(guild, member, o))) {
                        event.getHook().sendMessage("Your choice from this menu is final and can't be changed.").queue();
                        return;
                    }
                    applyOption(guild, member, clicked, true, Set.of(), added, removed, blocked);
                }
                default -> { // NORMAL
                    if (!alreadyHas && config.getMaxSelectable() != null
                            && countHeld(guild, member, options) >= config.getMaxSelectable()) {
                        event.getHook().sendMessage("You can only have up to " + config.getMaxSelectable()
                                + " role(s) from this menu - remove one first.").queue();
                        return;
                    }
                    applyOption(guild, member, clicked, !alreadyHas, Set.of(), added, removed, blocked);
                }
            }

            event.getHook().sendMessage(summarize(added, removed, blocked)).queue();
            logSelfService(guild, member, configId, added, removed, blocked);
        }));
    }

    /** True if the member holds every one of this option's roles that still exist on the server. */
    private boolean hasOption(Guild guild, Member member, RoleMenuOption option) {
        boolean anyExists = false;
        for (String roleId : option.roleIds()) {
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                continue;
            }
            anyExists = true;
            if (!member.getRoles().contains(role)) {
                return false;
            }
        }
        return anyExists;
    }

    /** Adds (grant=true) whichever of the option's roles the member doesn't yet have, or removes
     *  (grant=false) whichever it does - deleted roles are skipped, unmanageable roles are reported.
     *  {@code protectFromRemoval} is only consulted when {@code grant} is false: a role another,
     *  currently-wanted option also bundles is never stripped away just because this call is
     *  clearing a different (sibling/deselected) option that happens to share it. */
    private void applyOption(Guild guild, Member member, RoleMenuOption option, boolean grant, Set<String> protectFromRemoval,
            List<String> added, List<String> removed, List<String> blocked) {
        for (String roleId : option.roleIds()) {
            if (!grant && protectFromRemoval.contains(roleId)) {
                continue;
            }
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                continue;
            }
            boolean has = member.getRoles().contains(role);
            if (grant == has) {
                continue;
            }
            if (!canManageRole(guild, role)) {
                blocked.add(role.getName());
                continue;
            }
            if (grant) {
                guild.addRoleToMember(member, role).queue();
                added.add(role.getName());
            } else {
                guild.removeRoleFromMember(member, role).queue();
                removed.add(role.getName());
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(PREFIX)) {
            return;
        }
        Long configId = parseId(id.substring(PREFIX.length()));
        if (configId == null) {
            return;
        }
        if (event.getGuild() == null) {
            return;
        }

        event.deferReply(true).queue(); // see onButtonInteraction - ack before the DB reads
        event.getHook().setEphemeral(true);
        withDbGuard(event, () -> withConfig(event, configId, config -> {
            String denied = accessDeniedReason(event.getGuild(), event.getMember(), config);
            if (denied != null) {
                event.getHook().sendMessage(denied).queue();
                return;
            }

            Guild guild = event.getGuild();
            Member member = event.getMember();
            Set<String> selectedIndexes = new HashSet<>(event.getValues());
            List<RoleMenuOption> options = roleMenuService.readOptions(config.getOptions());
            String messageType = config.getMessageType();

            if ("BINDING".equals(messageType) && options.stream().anyMatch(o -> hasOption(guild, member, o))) {
                event.getHook().sendMessage("Your choice from this menu is final and can't be changed.").queue();
                return;
            }

            // DROP never adds; VERIFY/BINDING never remove an unselected-but-held option - the
            // select menu's own checkboxes only express what the member is actively asking for.
            boolean canAdd = !"DROP".equals(messageType);
            boolean canRemove = "NORMAL".equals(messageType) || "UNIQUE".equals(messageType) || "DROP".equals(messageType);

            if (canAdd && config.getMaxSelectable() != null) {
                long currentlyHeld = countHeld(guild, member, options);
                long newPicks = 0;
                for (int i = 0; i < options.size(); i++) {
                    if (selectedIndexes.contains(String.valueOf(i)) && !hasOption(guild, member, options.get(i))) {
                        newPicks++;
                    }
                }
                if (currentlyHeld + newPicks > config.getMaxSelectable()) {
                    event.getHook().sendMessage(
                            "You can select at most " + config.getMaxSelectable() + " role(s) from this menu.").queue();
                    return;
                }
            }

            // Roles bundled into a selected option are never stripped by a deselected sibling that
            // happens to share them (e.g. "2. ROCNIK + API" selected while plain "API" is not).
            Set<String> keep = new HashSet<>();
            for (int i = 0; i < options.size(); i++) {
                if (selectedIndexes.contains(String.valueOf(i))) {
                    keep.addAll(options.get(i).roleIds());
                }
            }

            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            List<String> blocked = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                RoleMenuOption option = options.get(i);
                boolean shouldHave = selectedIndexes.contains(String.valueOf(i));
                boolean has = hasOption(guild, member, option);
                if (shouldHave == has) {
                    continue;
                }
                if (shouldHave && !canAdd) {
                    continue;
                }
                if (!shouldHave && !canRemove) {
                    continue;
                }
                applyOption(guild, member, option, shouldHave, keep, added, removed, blocked);
            }

            event.getHook().sendMessage(summarize(added, removed, blocked)).queue();
            logSelfService(guild, member, configId, added, removed, blocked);
        }));
    }

    /**
     * Runs the (DB-touching) handler body; if the database is unreachable - config lookup or the
     * verification check on a cold cache during a Postgres outage - the interaction has already
     * been deferred, so resolve the spinner with a message instead of letting it hang or bubbling
     * out as an uncaught listener exception.
     */
    private void withDbGuard(GenericComponentInteractionCreateEvent event, Runnable body) {
        try {
            body.run();
        } catch (TransactionException | DataAccessException e) {
            String guildId = event.getGuild() == null ? "?" : event.getGuild().getId();
            log.warn("Role menu interaction hit an unavailable database in guild {}: {}", guildId, e.getMessage());
            event.getHook().sendMessage("⚠️ Databáza je dočasne nedostupná, skús to o chvíľu.").queue();
        }
    }

    private void withConfig(GenericComponentInteractionCreateEvent event, Long configId,
            Consumer<RoleMenuConfig> action) {
        Guild guild = event.getGuild();
        if (guild == null
                || adminSettingsService.isMaintenanceMode()
                || !adminSettingsService.get("rolemenu_enabled_" + guild.getId(), Boolean.class, false)) {
            // Nothing to say to the user - drop the deferred "thinking..." placeholder.
            event.getHook().deleteOriginal().queue(ok -> { }, err -> { });
            return;
        }
        roleMenuConfigRepository.findById(configId)
                .filter(config -> config.getGuildId().equals(guild.getId()))
                .ifPresentOrElse(action, () -> event.getHook().deleteOriginal().queue(ok -> { }, err -> { }));
    }

    /** Returns null if the member may use this menu, or the rejection message to reply with. */
    private String accessDeniedReason(Guild guild, Member member, RoleMenuConfig config) {
        if (member == null) {
            return "Something went wrong.";
        }

        List<String> blockedRoleIds = roleMenuService.readRoleIds(config.getBlockedRoleIds());
        if (!blockedRoleIds.isEmpty() && member.getRoles().stream().anyMatch(r -> blockedRoleIds.contains(r.getId()))) {
            return "You're not allowed to use this role menu.";
        }

        List<String> allowedRoleIds = roleMenuService.readRoleIds(config.getAllowedRoleIds());
        if (!allowedRoleIds.isEmpty() && member.getRoles().stream().noneMatch(r -> allowedRoleIds.contains(r.getId()))) {
            return "You don't have permission to use this role menu.";
        }

        if (config.isRequireVerified() && !memberVerificationChecker.isVerified(guild, member)) {
            return "You need to verify first (/verify) before using this role menu.";
        }

        return null;
    }

    /** Number of this menu's options the member currently holds in full. */
    private long countHeld(Guild guild, Member member, List<RoleMenuOption> options) {
        return options.stream().filter(o -> hasOption(guild, member, o)).count();
    }

    private boolean canManageRole(Guild guild, Role role) {
        return BotPermissionChecker.missingPermissions(guild, Permission.MANAGE_ROLES).isEmpty()
                && BotPermissionChecker.rolesAboveBot(guild, role).isEmpty();
    }

    /** Only worth a row when something actually happened or failed - a click that changed nothing
     *  (e.g. re-picking an already-held NORMAL option) would otherwise flood this at full click
     *  volume for zero informational value. */
    private void logSelfService(Guild guild, Member member, Long configId,
            List<String> added, List<String> removed, List<String> blocked) {
        if (added.isEmpty() && removed.isEmpty() && blocked.isEmpty()) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("configId", configId);
        details.put("added", added);
        details.put("removed", removed);
        details.put("blocked", blocked);
        try {
            auditLogService.log(new AuditLogEntry(
                    "rolemenu", "Role menu self-service", guild.getId(), guild.getName(), null, null,
                    member.getId(), member.getUser().getName(), details));
        } catch (Exception e) {
            log.error("Failed to log role menu self-service for {} in guild {}", member.getId(), guild.getId(), e);
        }
    }

    private String summarize(List<String> added, List<String> removed, List<String> blocked) {
        StringBuilder sb = new StringBuilder();
        if (added.isEmpty() && removed.isEmpty()) {
            sb.append("No changes.");
        } else {
            if (!added.isEmpty()) {
                sb.append("Added: ").append(String.join(", ", added));
            }
            if (!removed.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("Removed: ").append(String.join(", ", removed));
            }
        }
        if (!blocked.isEmpty()) {
            sb.append("\nCouldn't update: ").append(String.join(", ", blocked)).append(" (contact a moderator).");
        }
        return sb.toString();
    }

    private Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
