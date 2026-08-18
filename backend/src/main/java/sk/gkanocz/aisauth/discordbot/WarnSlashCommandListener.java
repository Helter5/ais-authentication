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
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.warn.Warn;
import sk.gkanocz.aisauth.warn.WarnService;
import sk.gkanocz.aisauth.warn.WarnThreshold;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class WarnSlashCommandListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Duration THRESHOLD_TIMEOUT_DURATION = Duration.ofHours(24);

    private final WarnService warnService;
    private final AuditLogService auditLogService;
    private final DiscordModerationService moderationService;
    private final EventLogEmbedSender eventLogEmbedSender;
    private final LogRoutingService logRoutingService;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        if ("mywarns".equals(event.getName())) {
            handleMyWarns(event, ephemeralOverride);
            return;
        }
        if (!"warn".equals(event.getName())) {
            return;
        }
        switch (event.getSubcommandName()) {
            case "add" -> handleWarn(event, ephemeralOverride);
            case "list" -> handleWarns(event, ephemeralOverride);
            case "remove" -> handleRemoveWarn(event, ephemeralOverride);
            case "clearall" -> handleClearWarns(event, ephemeralOverride);
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
        if (!requireLogChannel(event, guildId, LogEventType.WARN_ISSUED)) {
            return;
        }

        try {
            long prospectiveCount = warnService.countWarns(target.getId(), guildId) + 1;
            Optional<WarnThreshold> active = warnService.activeThreshold(guildId, prospectiveCount);

            if (active.isPresent()) {
                String blocker = moderationService.missingPermission(target, active.get().getAction());
                if (blocker != null) {
                    event.getHook().sendMessage("⚠️ Warn not recorded: the \"" + active.get().getAction()
                            + "\" threshold at " + active.get().getWarnLimit() + " warns is in effect, but " + blocker + ".").queue();
                    return;
                }
            }

            warnService.addWarn(guildId, target.getId(), event.getUser().getId(), reason);
            long warnCount = warnService.countWarns(target.getId(), guildId);

            eventLogEmbedSender.send(event.getGuild(), LogEventType.WARN_ISSUED,
                    EventLogEmbedSender.base(EventLogEmbedSender.WARNING, "Warning Issued", null)
                            .addField("User", EventLogEmbedSender.userField(target.getId(), target.getUser().getName()), true)
                            .addField("Moderator", "<@" + event.getUser().getId() + ">", true)
                            .addField("Total Warns", String.valueOf(warnCount), true)
                            .addField("Reason", reason, false));

            Optional<WarnThreshold> matched = warnService.matchingThreshold(guildId, warnCount);

            Optional<PunishmentOutcome> outcome = matched.map(threshold -> applyPunishment(target, threshold, warnCount));

            StringBuilder message = new StringBuilder()
                    .append("**").append(target.getUser().getName()).append("** warned. Reason: ").append(reason)
                    .append("\nTotal warns: ").append(warnCount);
            outcome.ifPresent(o -> {
                if (o.success()) {
                    message.append("\nAuto-action: ").append(o.detail());
                } else {
                    message.append("\n⚠️ Auto-action (").append(o.action()).append(") failed: ").append(o.detail());
                }
            });

            event.getHook().sendMessage(message.toString()).queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Warn command failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
    }

    private PunishmentOutcome applyPunishment(Member target, WarnThreshold threshold, long warnCount) {
        String action = threshold.getAction();
        if ("none".equals(action)) {
            return null;
        }
        String reason = "Reached " + warnCount + " warns";

        DiscordModerationService.Outcome outcome = moderationService.apply(target, action, reason, THRESHOLD_TIMEOUT_DURATION);
        if (outcome == null) {
            return null;
        }
        if (!outcome.success()) {
            log.error("Auto-punishment failed: {}", outcome.detail());
        }
        auditLogService.log(new AuditLogEntry(
                "automod", "Warning threshold " + action,
                target.getGuild().getId(), target.getGuild().getName(),
                null, null, target.getId(), target.getUser().getName(),
                outcome.success()
                        ? Map.of("status", "success", "warningCount", warnCount, "result", outcome.detail())
                        : Map.of("status", "failed", "warningCount", warnCount, "error", outcome.detail())));

        eventLogEmbedSender.send(target.getGuild(), LogEventType.WARN_THRESHOLD_ACTION,
                EventLogEmbedSender.base(outcome.success() ? EventLogEmbedSender.WARNING : EventLogEmbedSender.DANGER,
                                "Warn Threshold Action", null)
                        .addField("User", EventLogEmbedSender.userField(target.getId(), target.getUser().getName()), true)
                        .addField("Warn Count", String.valueOf(warnCount), true)
                        .addField("Result", outcome.success() ? outcome.detail() + " applied" : action + " failed: " + outcome.detail(), false));

        return new PunishmentOutcome(action, outcome.success(), outcome.detail());
    }

    private record PunishmentOutcome(String action, boolean success, String detail) {
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
        if (!requireLogChannel(event, guildId, LogEventType.WARN_REMOVED)) {
            return;
        }

        try {
            Warn removed = warnService.removeWarn(warnId, guildId);
            event.getHook().sendMessage("Warn #" + warnId + " removed.").queue();

            eventLogEmbedSender.send(event.getGuild(), LogEventType.WARN_REMOVED,
                    EventLogEmbedSender.base(EventLogEmbedSender.SUCCESS, "Warning Removed", null)
                            .addField("Warn ID", "#" + warnId, true)
                            .addField("User", "<@" + removed.getDiscordId() + ">", true)
                            .addField("Moderator", "<@" + event.getUser().getId() + ">", true)
                            .addField("Original Reason", removed.getReason(), false));
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        }
    }

    private void handleClearWarns(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? false : ephemeralOverride).queue();
        User target = event.getOption("user").getAsUser();
        String guildId = event.getGuild().getId();
        if (!requireLogChannel(event, guildId, LogEventType.WARNS_CLEARED)) {
            return;
        }

        long cleared = warnService.clearWarns(target.getId(), guildId);
        if (cleared == 0) {
            event.getHook().sendMessage(target.getName() + " has no warnings.").queue();
            return;
        }
        event.getHook().sendMessage("Cleared " + cleared + " warning(s) for " + target.getName() + ".").queue();

        eventLogEmbedSender.send(event.getGuild(), LogEventType.WARNS_CLEARED,
                EventLogEmbedSender.base(EventLogEmbedSender.SUCCESS, "Warnings Cleared", null)
                        .addField("User", EventLogEmbedSender.userField(target.getId(), target.getName()), true)
                        .addField("Moderator", "<@" + event.getUser().getId() + ">", true)
                        .addField("Warnings Cleared", String.valueOf(cleared), true));
    }

    /** Refuses the subcommand outright when its log channel isn't configured, rather than silently moderating with no trail. */
    private boolean requireLogChannel(SlashCommandInteractionEvent event, String guildId, LogEventType eventType) {
        if (logRoutingService.channelIdFor(guildId, eventType).isPresent()) {
            return true;
        }
        event.getHook().sendMessage("Log kanál pre tento príkaz nie je nastavený. Kontaktuj administrátora.").queue();
        return false;
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
