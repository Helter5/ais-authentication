package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.verification.VerificationFacade;
import sk.gkanocz.aisauth.verification.VerificationService;
import sk.gkanocz.aisauth.verification.VerifiedUser;

import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
class VerificationSlashCommandListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VerificationFacade verificationFacade;
    private final VerificationService verificationService;
    private final GuildSettingsService guildSettingsService;

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

        String aisId = event.getOption("ais_id").getAsString();
        String discordId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        try {
            verificationFacade.initiateAndNotify(discordId, guildId, aisId);
            event.getHook().sendMessage(
                    "Verifikačný email poslaný! Pozri si STUBA mail a potvrď kód cez `/code <kód>`. Kód platí 15 minút.")
                    .queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Verify command failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
    }

    private void handleCode(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();

        String code = event.getOption("code").getAsString();
        String discordId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        try {
            verificationService.confirmVerification(discordId, guildId, code);
            assignVerifiedRoleIfConfigured(event.getGuild(), event.getMember());
            event.getHook().sendMessage("Úspešne overené! Vitaj.").queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Code command failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
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
                + "\n**Email:** " + user.getEmail()
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

        try {
            verificationService.manuallyVerify(target.getId(), guildId, aisId, email);
            assignVerifiedRoleIfConfigured(event.getGuild(), target);
            event.getHook().sendMessage(
                    "<@" + target.getId() + "> has been manually verified with AIS ID `" + aisId + "`.").queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Manual verify command failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
    }

    private void assignVerifiedRoleIfConfigured(Guild guild, Member member) {
        if (member == null) {
            return;
        }
        String verifiedRoleId = guildSettingsService.getOrCreate(guild.getId()).getVerifiedRoleId();
        if (verifiedRoleId == null) {
            return;
        }
        Role role = guild.getRoleById(verifiedRoleId);
        if (role == null) {
            log.warn("Configured verified role {} not found in guild {}", verifiedRoleId, guild.getId());
            return;
        }
        guild.addRoleToMember(member, role).reason("Verified").queue(
                success -> { },
                failure -> log.warn("Failed to assign verified role to {}: {}", member.getId(), failure.getMessage()));
    }
}
