package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.verification.VerificationFacade;
import sk.gkanocz.aisauth.verification.VerificationService;
import sk.gkanocz.aisauth.verification.VerifiedUser;

import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
class VerificationSlashCommandListener extends ListenerAdapter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VerificationFacade verificationFacade;
    private final VerificationService verificationService;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "verify" -> handleVerify(event);
            case "code" -> handleCode(event);
            case "find" -> handleFind(event);
            case "manualverify" -> handleManualVerify(event);
            default -> {
            }
        }
    }

    private void handleVerify(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

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

    private void handleCode(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        String code = event.getOption("code").getAsString();
        String discordId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        try {
            verificationService.confirmVerification(discordId, guildId, code);
            event.getHook().sendMessage("Úspešne overené! Vitaj.").queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Code command failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
    }

    private void handleFind(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

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

    private void handleManualVerify(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        User target = event.getOption("user").getAsUser();
        String aisId = event.getOption("ais_id").getAsString();
        String email = event.getOption("email").getAsString();
        String guildId = event.getGuild().getId();

        try {
            verificationService.manuallyVerify(target.getId(), guildId, aisId, email);
            event.getHook().sendMessage(
                    "<@" + target.getId() + "> has been manually verified with AIS ID `" + aisId + "`.").queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Manual verify command failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
    }
}
