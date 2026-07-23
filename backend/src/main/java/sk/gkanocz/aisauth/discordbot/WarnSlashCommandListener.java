package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.warn.Warn;
import sk.gkanocz.aisauth.warn.WarnService;
import sk.gkanocz.aisauth.warn.WarnThreshold;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
class WarnSlashCommandListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final WarnService warnService;
    private final AuditLogService auditLogService;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        switch (event.getName()) {
            case "warn" -> handleWarn(event, ephemeralOverride);
            case "warns" -> handleWarns(event, ephemeralOverride);
            case "mywarns" -> handleMyWarns(event, ephemeralOverride);
            case "removewarn" -> handleRemoveWarn(event, ephemeralOverride);
            case "clearwarns" -> handleClearWarns(event, ephemeralOverride);
            default -> {
            }
        }
    }

    private void handleWarn(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? false : ephemeralOverride).queue();

        Member target = event.getOption("user").getAsMember();
        String reason = event.getOption("reason").getAsString();
        String guildId = event.getGuild().getId();

        if (target == null) {
            event.getHook().sendMessage("User not found in this server.").queue();
            return;
        }

        try {
            warnService.addWarn(guildId, target.getId(), event.getUser().getId(), reason);
            long warnCount = warnService.countWarns(target.getId(), guildId);
            Optional<WarnThreshold> matched = warnService.matchingThreshold(guildId, warnCount);

            String actionTaken = matched.map(threshold -> applyPunishment(target, threshold, warnCount)).orElse(null);

            StringBuilder message = new StringBuilder()
                    .append("**").append(target.getUser().getName()).append("** warned. Reason: ").append(reason)
                    .append("\nTotal warns: ").append(warnCount);
            if (actionTaken != null) {
                message.append("\nAuto-action: ").append(actionTaken);
            }

            event.getHook().sendMessage(message.toString()).queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Warn command failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
    }

    private String applyPunishment(Member target, WarnThreshold threshold, long warnCount) {
        String action = threshold.getAction();
        String reason = "Reached " + warnCount + " warns";
        String result;

        try {
            switch (action) {
                case "ban" -> {
                    target.ban(0, TimeUnit.SECONDS).reason(reason).complete();
                    result = "banned";
                }
                case "kick" -> {
                    target.kick().reason(reason).complete();
                    result = "kicked";
                }
                case "timeout" -> {
                    target.timeoutFor(Duration.ofHours(24)).reason(reason).complete();
                    result = "timed out (24h)";
                }
                default -> {
                    return null;
                }
            }

            auditLogService.log(new AuditLogEntry(
                    "automod", "Warning threshold " + action,
                    target.getGuild().getId(), target.getGuild().getName(),
                    null, null, target.getId(), target.getUser().getName(),
                    Map.of("status", "success", "warningCount", warnCount, "result", result)));

            return result;
        } catch (Exception e) {
            log.error("Auto-punishment failed", e);
            auditLogService.log(new AuditLogEntry(
                    "automod", "Warning threshold " + action,
                    target.getGuild().getId(), target.getGuild().getName(),
                    null, null, target.getId(), target.getUser().getName(),
                    Map.of("status", "failed", "warningCount", warnCount, "error", String.valueOf(e.getMessage()))));
            return null;
        }
    }

    private void handleWarns(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();

        OptionMapping userOption = event.getOption("user");
        String guildId = event.getGuild().getId();

        if (userOption == null) {
            List<Warn> warns = warnService.getGuildWarns(guildId);
            event.getHook().sendMessage(formatWarnList("Server warnings", warns, true)).queue();
            return;
        }

        User target = userOption.getAsUser();
        List<Warn> warns = warnService.getWarns(target.getId(), guildId);
        event.getHook().sendMessage(formatWarnList("Warnings for " + target.getName(), warns, false)).queue();
    }

    private void handleMyWarns(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();
        List<Warn> warns = warnService.getWarns(event.getUser().getId(), event.getGuild().getId());
        event.getHook().sendMessage(formatWarnList("Your warnings", warns, false)).queue();
    }

    private void handleRemoveWarn(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? false : ephemeralOverride).queue();
        long warnId = event.getOption("id").getAsLong();
        String guildId = event.getGuild().getId();

        try {
            warnService.removeWarn(warnId, guildId);
            event.getHook().sendMessage("Warn #" + warnId + " removed.").queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        }
    }

    private void handleClearWarns(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? false : ephemeralOverride).queue();
        User target = event.getOption("user").getAsUser();
        String guildId = event.getGuild().getId();

        long cleared = warnService.clearWarns(target.getId(), guildId);
        if (cleared == 0) {
            event.getHook().sendMessage(target.getName() + " has no warnings.").queue();
            return;
        }
        event.getHook().sendMessage("Cleared " + cleared + " warning(s) for " + target.getName() + ".").queue();
    }

    private String formatWarnList(String title, List<Warn> warns, boolean includeTarget) {
        if (warns.isEmpty()) {
            return "**" + title + "**\nNo warnings.";
        }
        StringBuilder sb = new StringBuilder("**").append(title).append("** (").append(warns.size()).append(")\n");
        for (Warn warn : warns) {
            sb.append("#").append(warn.getId()).append(" ");
            if (includeTarget) {
                sb.append("<@").append(warn.getDiscordId()).append("> ");
            }
            sb.append("- ").append(warn.getReason())
                    .append(" (").append(warn.getCreatedAt().format(DATE_FORMAT)).append(")\n");
        }
        return sb.toString();
    }
}
