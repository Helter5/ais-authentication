package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.verification.VerificationFacade;
import sk.gkanocz.aisauth.verification.VerificationService;

@Slf4j
@Component
@RequiredArgsConstructor
class VerificationSlashCommandListener extends ListenerAdapter {

    private final VerificationFacade verificationFacade;
    private final VerificationService verificationService;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "verify" -> handleVerify(event);
            case "code" -> handleCode(event);
            default -> { }
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
}
