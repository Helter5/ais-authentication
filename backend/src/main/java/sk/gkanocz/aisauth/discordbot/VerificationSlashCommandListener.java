package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.verification.VerificationService;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import tools.jackson.core.type.TypeReference;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
class VerificationSlashCommandListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String VERIFICATION_DISABLED_MESSAGE =
            "Verifikácia je na tomto serveri momentálne vypnutá. Kontaktuj administrátora.";
    private static final String GENERIC_ERROR_MESSAGE = "Nastala neočakávaná chyba, skús to prosím neskôr.";
    /** Base text for the message sent right before /verify's work is queued - university-side
     * connection issues (see LdapStudentDirectoryService) can make this take up to ~90s, so this
     * tells the user it's still working instead of leaving them looking at a silent
     * "thinking..." indicator. {@link #verifyProgressMessage(int)} appends queue position when
     * there's one worth mentioning. */
    private static final String VERIFY_IN_PROGRESS_MESSAGE =
            "⏳ Overujem AIS ID, môže to trvať aj minútu (dočasný problém so sieťovým spojením na univerzitu).";
    private static final String DEFAULT_CODE_SUCCESS_MESSAGE = "Úspešne overené! Vitaj.";
    /** {channel=123456789012345678} - one token per referenced channel, so a single message can
     * link to as many different channels (roles, rules, welcome, ...) as an admin wants, not just one. */
    private static final Pattern CHANNEL_TOKEN = Pattern.compile("\\{channel=(\\d+)}");

    private final VerificationService verificationService;
    private final GuildSettingsService guildSettingsService;
    private final LogRoutingService logRoutingService;
    private final VerifiedRoleResolver verifiedRoleResolver;
    private final VerifyRateLimiter verifyRateLimiter;
    private final EventLogEmbedSender eventLogEmbedSender;
    private final VerificationProperties verificationProperties;
    private final PendingVerificationStore pendingVerificationStore;
    private final AdminSettingsService adminSettingsService;
    private final AuditLogService auditLogService;

    /**
     * /verify's LDAP round-trip (throttled to 1 req/sec by LdapRequestThrottle, and now bounded by
     * an LDAP connect/read timeout) runs here instead of on JDA's own event-dispatch thread. Without
     * this, a slow or hung LDAP call (e.g. the university VPN tunnel resetting mid-request) would
     * tie up a JDA dispatch thread for its whole duration; with only a handful of those threads and
     * every /verify call serialized behind the same throttle, a burst of ~10+ concurrent /verify
     * calls could starve the pool and make the *entire* bot stop responding, not just /verify.
     * Sized at {@value #VERIFY_POOL_SIZE} rather than the LDAP throttle's 1 req/sec (a single-digit
     * pool would be enough for that alone) so a burst of /verify calls during one of the ~60-65s
     * LDAP dead windows - where each call can sit blocked for up to the full 90s read timeout -
     * doesn't queue callers up behind each other on top of that wait.
     */
    private static final int VERIFY_POOL_SIZE = 10;
    private final ExecutorService verifyExecutor = Executors.newFixedThreadPool(VERIFY_POOL_SIZE);
    /** In-flight-or-queued count on {@link #verifyExecutor}, purely to tell a caller stuck behind
     * a full pool roughly how far back they are - not a source of truth for anything else. */
    private final AtomicInteger verifyInFlight = new AtomicInteger(0);

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        switch (event.getName()) {
            case "verify" -> handleVerify(event, ephemeralOverride);
            case "code" -> handleCode(event, ephemeralOverride);
            case "find" -> handleFind(event, ephemeralOverride);
            case "manualverify" -> handleManualVerify(event, ephemeralOverride);
            default -> {
            }
        }
    }

    private void handleVerify(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride == null ? true : ephemeralOverride;
        event.deferReply(ephemeral).queue();
        // deferReply's ephemeral flag only covers the initial "thinking..." placeholder - every
        // hook.sendMessage(...) followup below is a separate message that does NOT inherit it on
        // its own. This makes the hook itself default new followups to the same visibility.
        event.getHook().setEphemeral(ephemeral);

        String discordId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        if (!guildSettingsService.getOrCreate(guildId).isVerificationEnabled()) {
            event.getHook().sendMessage(VERIFICATION_DISABLED_MESSAGE).queue();
            return;
        }
        if (logRoutingService.channelIdFor(guildId, LogEventType.VERIFY_REQUESTED).isEmpty()) {
            event.getHook().sendMessage(
                    "Verifikačný log kanál nie je nastavený. Kontaktuj administrátora.").queue();
            return;
        }
        try {
            verifiedRoleResolver.resolveAssignable(event.getGuild());
        } catch (VerifiedRoleUnavailableException e) {
            event.getHook().sendMessage("Verifikácia momentálne nie je dostupná: " + e.getMessage()).queue();
            return;
        }
        String aisId = event.getOption("ais_id").getAsString();

        // Cheap, LDAP-free checks (AIS ID format, already verified) first - none of these should
        // burn a rate-limit attempt, since the request was never going to reach LDAP anyway.
        try {
            verificationService.assertValidAndNotAlreadyVerified(discordId, guildId, aisId);
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
            return;
        }

        // Sent immediately, before the submit() below, so a caller stuck behind a full
        // verifyExecutor pool (see its javadoc) gets feedback right away instead of silently
        // sitting on Discord's default "thinking..." indicator until a worker thread frees up.
        // position counts this call itself, so position <= VERIFY_POOL_SIZE means "you get a
        // worker thread right away" and only a larger position is actually queued behind others.
        int position = verifyInFlight.incrementAndGet();
        // Every later outcome (rate-limited / eligible / error) edits *this* message by id instead
        // of sending a new followup, so the user sees one message update in place rather than a
        // fresh "Confirm/Cancel" embed appearing below the now-stale "Overujem..." text. Blocks
        // (.complete()) rather than .queue(callback) - checkEligibility below can finish faster
        // than the async send round-trip when LDAP responds quickly (the common case, not the
        // rare one), which raced editMessageById(...) against a still-null id.
        Message progressMessage = event.getHook().sendMessage(verifyProgressMessage(position)).complete();
        String progressMessageId = progressMessage.getId();

        // Throttle + LDAP lookup off the JDA dispatch thread - see verifyExecutor's javadoc.
        verifyExecutor.submit(() -> {
            try {
                if (!verificationProperties.testingMode()) {
                    Optional<Long> waitMinutes = verifyRateLimiter.checkAndRecordAttempt(discordId, guildId);
                    if (waitMinutes.isPresent()) {
                        replaceProgressMessage(event, progressMessageId,
                                "Vyčerpal si limit na príkaz /verify. Skús znova o " + waitMinutes.get() + " minút.");
                        return;
                    }
                }

                // Only a preview - runs LDAP + eligibility checks but writes nothing. The code is only
                // actually created and the email only actually sent once Confirm is clicked
                // (VerifyConfirmationButtonListener), so a fat-fingered AIS ID can be caught here first.
                String email = verificationService.checkEligibility(discordId, guildId, aisId);
                String token = pendingVerificationStore.create(discordId, guildId, aisId);

                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(new Color(0x6366F1))
                        .setTitle("Potvrdenie verifikácie")
                        .setDescription("Chystáš sa verifikovať s AIS ID: **" + aisId + "**\n"
                                + "Email bude poslaný na: **" + email + "**");

                event.getHook().editMessageById(progressMessageId, "")
                        .setEmbeds(embed.build())
                        .setActionRow(
                                Button.success("verify_confirm:" + token, "Confirm"),
                                Button.danger("verify_cancel:" + token, "Cancel"))
                        .queue();
            } catch (DomainException e) {
                replaceProgressMessage(event, progressMessageId, e.getMessage());
            } catch (Exception e) {
                log.error("Verify command failed", e);
                replaceProgressMessage(event, progressMessageId, GENERIC_ERROR_MESSAGE);
            } finally {
                verifyInFlight.decrementAndGet();
            }
        });
    }

    /** Replaces the "⏳ Overujem..." progress message in place with a plain-text result, clearing
     * any embed/buttons it might have had (there aren't any yet at this point, but keeps the
     * method safe to reuse if that ever changes). */
    private void replaceProgressMessage(SlashCommandInteractionEvent event, String progressMessageId, String content) {
        event.getHook().editMessageById(progressMessageId, content)
                .setEmbeds(List.of())
                .setComponents(List.of())
                .queue();
    }

    // Package-private (not private) purely so the message-formatting logic is directly unit
    // testable without needing to force real thread-pool queueing.
    String verifyProgressMessage(int position) {
        if (position <= VERIFY_POOL_SIZE) {
            return VERIFY_IN_PROGRESS_MESSAGE;
        }
        int othersAheadInQueue = position - VERIFY_POOL_SIZE - 1;
        return VERIFY_IN_PROGRESS_MESSAGE + " Si vo fronte" + (othersAheadInQueue > 0
                ? " (" + othersAheadInQueue + " pred tebou)." : ", si na rade ako prvý.");
    }

    private void handleCode(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride == null ? true : ephemeralOverride;
        event.deferReply(ephemeral).queue();
        event.getHook().setEphemeral(ephemeral); // see handleVerify - followups don't inherit deferReply's flag

        String code = event.getOption("code").getAsString();
        String discordId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        if (!guildSettingsService.getOrCreate(guildId).isVerificationEnabled()) {
            event.getHook().sendMessage(VERIFICATION_DISABLED_MESSAGE).queue();
            return;
        }
        if (logRoutingService.channelIdFor(guildId, LogEventType.CODE_CONFIRMED).isEmpty()) {
            event.getHook().sendMessage(
                    "Verifikačný log kanál nie je nastavený. Kontaktuj administrátora.").queue();
            return;
        }

        Role role;
        try {
            role = verifiedRoleResolver.resolveAssignable(event.getGuild());
        } catch (VerifiedRoleUnavailableException e) {
            event.getHook().sendMessage("Verifikácia momentálne nie je dostupná: " + e.getMessage()).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            event.getHook().sendMessage("Verifikácia zlyhala — nie si členom tohto servera.").queue();
            return;
        }

        try {
            VerifiedUser verifiedUser = verificationService.confirmVerification(discordId, guildId, code);

            if (!assignRole(event.getGuild(), member, role, "Verified")) {
                verificationService.revertVerification(verifiedUser);
                event.getHook().sendMessage(
                        "Verifikácia zlyhala — nemôžem ti priradiť rolu. Kontaktuj administrátora.").queue();
                return;
            }
            clearInactiveRole(event.getGuild(), member);

            auditLogService.log(new AuditLogEntry(
                    "verification", "Verified via /code",
                    event.getGuild().getId(), event.getGuild().getName(),
                    event.getChannel().getId(), event.getChannel().getName(),
                    discordId, event.getUser().getName(),
                    Map.of("aisId", verifiedUser.getAisId())));

            eventLogEmbedSender.send(event.getGuild(), LogEventType.CODE_CONFIRMED,
                    EventLogEmbedSender.base(EventLogEmbedSender.SUCCESS, "User Verified", null)
                            .addField("User", EventLogEmbedSender.userField(discordId, event.getUser().getName()), true)
                            .addField("AIS ID", verifiedUser.getAisId(), true)
                            .addField("Channel", "<#" + event.getChannel().getId() + ">", true));
            event.getHook().sendMessage(codeSuccessMessage(event, guildId, verifiedUser)).queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Code command failed", e);
            event.getHook().sendMessage(GENERIC_ERROR_MESSAGE).queue();
        }
    }

    /**
     * "cmd_settings_<guild>_code" -> "message" - configurable via the /code card's Settings modal
     * (Commands page), falls back to the hardcoded default when unset/blank. Every {channel=<id>}
     * token (there can be several, one per referenced channel) is turned into a real clickable
     * Discord channel mention - these are admin-picked channels, not the one /code was run in.
     */
    private String codeSuccessMessage(SlashCommandInteractionEvent event, String guildId, VerifiedUser verifiedUser) {
        Map<String, Object> settings = adminSettingsService.get(
                "cmd_settings_" + guildId + "_code", new TypeReference<Map<String, Object>>() { }, Map.of());
        Object configuredMessage = settings.get("message");
        String template = (configuredMessage instanceof String s && !s.isBlank()) ? s : DEFAULT_CODE_SUCCESS_MESSAGE;

        Matcher matcher = CHANNEL_TOKEN.matcher(template);
        String withChannels = matcher.replaceAll(result -> "<#" + result.group(1) + ">");

        return withChannels
                .replace("{user}", event.getUser().getName())
                .replace("{server}", event.getGuild().getName())
                .replace("{ais_id}", verifiedUser.getAisId());
    }

    private void handleFind(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride == null ? true : ephemeralOverride;
        event.deferReply(ephemeral).queue();
        event.getHook().setEphemeral(ephemeral); // see handleVerify - followups don't inherit deferReply's flag

        String aisId = event.getOption("ais_id").getAsString();
        String guildId = event.getGuild().getId();

        VerifiedUser user = verificationService.findVerifiedUser(aisId, guildId).orElse(null);
        if (user == null) {
            event.getHook().sendMessage("No verified user found with AIS ID " + aisId + ".").queue();
            return;
        }

        String message = "**AIS ID:** " + user.getAisId()
                + "\n**Discord:** <@" + user.getDiscordId() + ">"
                + "\n**Verified at:** " + user.getVerifiedAt().format(DATE_FORMAT);
        event.getHook().sendMessage(message).queue();
    }

    private void handleManualVerify(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride == null ? true : ephemeralOverride;
        event.deferReply(ephemeral).queue();
        event.getHook().setEphemeral(ephemeral); // see handleVerify - followups don't inherit deferReply's flag

        Member target = event.getOption("user").getAsMember();
        String aisId = event.getOption("ais_id").getAsString();
        String email = event.getOption("email").getAsString();
        String guildId = event.getGuild().getId();

        if (target == null) {
            event.getHook().sendMessage("User not found in this server.").queue();
            return;
        }
        if (logRoutingService.channelIdFor(guildId, LogEventType.MANUAL_VERIFY_PERFORMED).isEmpty()) {
            event.getHook().sendMessage(
                    "Verifikačný log kanál nie je nastavený. Kontaktuj administrátora.").queue();
            return;
        }

        Role role;
        try {
            role = verifiedRoleResolver.resolveAssignable(event.getGuild());
        } catch (VerifiedRoleUnavailableException e) {
            event.getHook().sendMessage("Verifikácia momentálne nie je dostupná: " + e.getMessage()).queue();
            return;
        }

        try {
            VerifiedUser verifiedUser = verificationService.manuallyVerify(target.getId(), guildId, aisId, email);

            if (!assignRole(event.getGuild(), target, role, "Manually verified")) {
                verificationService.revertVerification(verifiedUser);
                event.getHook().sendMessage(
                        "Verifikácia zlyhala — nemôžem priradiť rolu **" + role.getName()
                                + "**. Databázový záznam bol vrátený späť.").queue();
                return;
            }
            clearInactiveRole(event.getGuild(), target);

            auditLogService.log(new AuditLogEntry(
                    "verification", "Verified via /manualverify",
                    event.getGuild().getId(), event.getGuild().getName(),
                    event.getChannel().getId(), event.getChannel().getName(),
                    target.getId(), target.getUser().getName(),
                    Map.of("aisId", aisId, "performedBy", event.getUser().getName() + " (" + event.getUser().getId() + ")")));

            eventLogEmbedSender.send(event.getGuild(), LogEventType.MANUAL_VERIFY_PERFORMED,
                    EventLogEmbedSender.base(EventLogEmbedSender.WARNING, "Manual Verification", null)
                            .addField("User", EventLogEmbedSender.userField(target.getId(), target.getUser().getName()), true)
                            .addField("AIS ID", aisId, true)
                            .addField("Performed by", "<@" + event.getUser().getId() + ">", true));
            event.getHook().sendMessage(
                    "<@" + target.getId() + "> has been manually verified with AIS ID `" + aisId + "`.").queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Manual verify command failed", e);
            event.getHook().sendMessage(GENERIC_ERROR_MESSAGE).queue();
        }
    }

    /**
     * Blocks (.complete()) instead of the fire-and-forget .queue() the old silent version used -
     * callers need to know synchronously whether the role actually landed so they can roll back the
     * just-created VerifiedUser row on failure instead of reporting success anyway.
     */
    private boolean assignRole(Guild guild, Member member, Role role, String reason) {
        if (member.getRoles().contains(role)) {
            return true;
        }
        try {
            guild.addRoleToMember(member, role).reason(reason).complete();
            return true;
        } catch (Exception e) {
            log.error("Failed to assign verified role {} to {} in guild {}: {}",
                    role.getId(), member.getId(), guild.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * A member wiped while inactive (e.g. between Bc. and Ing. studies) keeps the inactive/ex-student
     * role forever otherwise - re-verifying should clear it now that they hold the verified role again.
     */
    private void clearInactiveRole(Guild guild, Member member) {
        try {
            String inactiveRoleId = guildSettingsService.getOrCreate(guild.getId()).getInactiveRoleId();
            if (inactiveRoleId == null) {
                return;
            }
            Role inactiveRole = guild.getRoleById(inactiveRoleId);
            if (inactiveRole == null || !member.getRoles().contains(inactiveRole)) {
                return;
            }
            guild.removeRoleFromMember(member, inactiveRole).reason("Re-verified").complete();
        } catch (Exception e) {
            log.error("Failed to remove inactive role from {} in guild {}: {}",
                    member.getId(), guild.getId(), e.getMessage());
        }
    }
}
