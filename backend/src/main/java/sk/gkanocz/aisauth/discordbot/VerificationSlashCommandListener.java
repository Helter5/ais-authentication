package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;
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
import java.util.Map;
import java.util.Optional;
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
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();

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

        if (!verificationProperties.testingMode()) {
            Optional<Long> waitMinutes = verifyRateLimiter.checkAndRecordAttempt(discordId, guildId);
            if (waitMinutes.isPresent()) {
                event.getHook().sendMessage(
                        "Vyčerpal si limit na príkaz /verify. Skús znova o " + waitMinutes.get() + " minút.").queue();
                return;
            }
        }

        try {
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

            event.getHook().sendMessageEmbeds(embed.build())
                    .addActionRow(
                            Button.success("verify_confirm:" + token, "Confirm"),
                            Button.danger("verify_cancel:" + token, "Cancel"))
                    .queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Verify command failed", e);
            event.getHook().sendMessage(GENERIC_ERROR_MESSAGE).queue();
        }
    }

    private void handleCode(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();

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
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();

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
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();

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
}
