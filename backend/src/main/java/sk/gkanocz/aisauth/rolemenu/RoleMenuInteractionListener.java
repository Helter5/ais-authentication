package sk.gkanocz.aisauth.rolemenu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.discordbot.BotPermissionChecker;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.verification.MemberVerificationChecker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles clicks on a role menu's buttons/select-menu (posted by RoleMenuService). Not gated
 * through CommandInteractionListener since these are component interactions, not slash commands -
 * the guild allowlist check happens one layer up in GuildAllowlistEventManager instead.
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
        if (configId == null) {
            return;
        }
        String clickedRoleId = parts[1];

        withConfig(event.getGuild(), configId, config -> {
            String denied = accessDeniedReason(event.getGuild(), event.getMember(), config);
            if (denied != null) {
                event.reply(denied).setEphemeral(true).queue();
                return;
            }

            Guild guild = event.getGuild();
            Member member = event.getMember();
            Role clickedRole = guild.getRoleById(clickedRoleId);
            if (clickedRole == null) {
                event.reply("That role no longer exists.").setEphemeral(true).queue();
                return;
            }
            if (!canManageRole(guild, clickedRole)) {
                event.reply("I can't manage that role right now - contact a moderator.").setEphemeral(true).queue();
                return;
            }

            List<RoleMenuOption> options = roleMenuService.readOptions(config.getOptions());
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            boolean single = "SINGLE".equals(config.getSelectionMode());

            if (single) {
                for (RoleMenuOption option : options) {
                    if (option.roleId().equals(clickedRoleId)) {
                        continue;
                    }
                    Role sibling = guild.getRoleById(option.roleId());
                    if (sibling != null && member.getRoles().contains(sibling) && canManageRole(guild, sibling)) {
                        guild.removeRoleFromMember(member, sibling).queue();
                        removed.add(sibling.getName());
                    }
                }
            }

            boolean alreadyHas = member.getRoles().contains(clickedRole);
            if (!alreadyHas && !single && config.getMaxSelectable() != null
                    && countHeld(member, guild, options) >= config.getMaxSelectable()) {
                event.reply("You can only have up to " + config.getMaxSelectable()
                        + " role(s) from this menu - remove one first.").setEphemeral(true).queue();
                return;
            }

            if (alreadyHas) {
                guild.removeRoleFromMember(member, clickedRole).queue();
                removed.add(clickedRole.getName());
            } else {
                guild.addRoleToMember(member, clickedRole).queue();
                added.add(clickedRole.getName());
            }

            event.reply(summarize(added, removed)).setEphemeral(true).queue();
        });
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

        withConfig(event.getGuild(), configId, config -> {
            String denied = accessDeniedReason(event.getGuild(), event.getMember(), config);
            if (denied != null) {
                event.reply(denied).setEphemeral(true).queue();
                return;
            }

            Guild guild = event.getGuild();
            Member member = event.getMember();
            Set<String> selected = new HashSet<>(event.getValues());
            List<RoleMenuOption> options = roleMenuService.readOptions(config.getOptions());

            if ("MULTI".equals(config.getSelectionMode()) && config.getMaxSelectable() != null
                    && selected.size() > config.getMaxSelectable()) {
                event.reply("You can select at most " + config.getMaxSelectable() + " role(s) from this menu.")
                        .setEphemeral(true).queue();
                return;
            }

            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            List<String> blocked = new ArrayList<>();
            for (RoleMenuOption option : options) {
                Role role = guild.getRoleById(option.roleId());
                if (role == null) {
                    continue;
                }
                boolean shouldHave = selected.contains(option.roleId());
                boolean has = member.getRoles().contains(role);
                if (shouldHave == has) {
                    continue;
                }
                if (!canManageRole(guild, role)) {
                    blocked.add(role.getName());
                    continue;
                }
                if (shouldHave) {
                    guild.addRoleToMember(member, role).queue();
                    added.add(role.getName());
                } else {
                    guild.removeRoleFromMember(member, role).queue();
                    removed.add(role.getName());
                }
            }

            String summary = summarize(added, removed);
            if (!blocked.isEmpty()) {
                summary += "\nCouldn't update: " + String.join(", ", blocked) + " (contact a moderator).";
            }
            event.reply(summary).setEphemeral(true).queue();
        });
    }

    private void withConfig(Guild guild, Long configId, java.util.function.Consumer<RoleMenuConfig> action) {
        if (guild == null) {
            return;
        }
        if (!adminSettingsService.get("rolemenu_enabled_" + guild.getId(), Boolean.class, false)) {
            return;
        }
        roleMenuConfigRepository.findById(configId)
                .filter(config -> config.getGuildId().equals(guild.getId()))
                .ifPresent(action);
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

    private long countHeld(Member member, Guild guild, List<RoleMenuOption> options) {
        return options.stream()
                .map(o -> guild.getRoleById(o.roleId()))
                .filter(r -> r != null && member.getRoles().contains(r))
                .count();
    }

    private boolean canManageRole(Guild guild, Role role) {
        return BotPermissionChecker.missingPermissions(guild, Permission.MANAGE_ROLES).isEmpty()
                && BotPermissionChecker.rolesAboveBot(guild, role).isEmpty();
    }

    private String summarize(List<String> added, List<String> removed) {
        if (added.isEmpty() && removed.isEmpty()) {
            return "No changes.";
        }
        StringBuilder sb = new StringBuilder();
        if (!added.isEmpty()) {
            sb.append("Added: ").append(String.join(", ", added));
        }
        if (!removed.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("Removed: ").append(String.join(", ", removed));
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
