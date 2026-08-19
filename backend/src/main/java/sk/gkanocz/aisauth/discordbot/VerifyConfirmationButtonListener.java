package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.verification.VerificationCode;
import sk.gkanocz.aisauth.verification.VerificationFacade;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles the verify_confirm:<token> / verify_cancel:<token> buttons posted alongside /verify's
 * confirmation embed - the actual code-creation + email send is deliberately deferred until here,
 * so a mistyped AIS ID (e.g. 126126 instead of 126127) can be caught before anything is sent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyConfirmationButtonListener extends ListenerAdapter {

    private static final String GENERIC_ERROR_MESSAGE = "Nastala neočakávaná chyba, skús to prosím neskôr.";
    /** See VerificationSlashCommandListener.VERIFY_IN_PROGRESS_MESSAGE - same reasoning, Confirm
     * hits LDAP again. */
    private static final String VERIFY_IN_PROGRESS_MESSAGE =
            "⏳ Overujem, môže to trvať aj minútu (dočasný problém so sieťovým spojením na univerzitu).";

    private final PendingVerificationStore pendingVerificationStore;
    private final VerificationFacade verificationFacade;
    private final EventLogEmbedSender eventLogEmbedSender;
    private final VerificationProperties verificationProperties;

    // Confirm hits LDAP again (throttled, same as /verify) - keep it off JDA's dispatch thread for
    // the same reason as VerificationSlashCommandListener.verifyExecutor, and sized the same way.
    private final ExecutorService confirmExecutor = Executors.newFixedThreadPool(10);

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("verify_confirm:") && !componentId.startsWith("verify_cancel:")) {
            return;
        }
        if (event.getGuild() == null) {
            return;
        }

        String[] parts = componentId.split(":", 2);
        String action = parts[0];
        String token = parts.length > 1 ? parts[1] : "";

        Optional<PendingVerificationStore.Pending> pendingOpt = pendingVerificationStore.get(token);
        if (pendingOpt.isEmpty()) {
            event.editMessage("Táto žiadosť už expirovala. Spusti `/verify` znova.")
                    .setEmbeds(List.of()).setComponents(List.of()).queue();
            return;
        }
        PendingVerificationStore.Pending pending = pendingOpt.get();
        if (!pending.discordId().equals(event.getUser().getId())) {
            event.reply("Toto nie je tvoja verifikácia.").setEphemeral(true).queue();
            return;
        }
        pendingVerificationStore.remove(token);

        if ("verify_cancel".equals(action)) {
            event.editMessage("Verifikácia zrušená.").setEmbeds(List.of()).setComponents(List.of()).queue();
            return;
        }

        event.deferEdit().queue();
        // Sent immediately, before submit() below, so a caller stuck behind a full
        // confirmExecutor pool gets feedback right away instead of sitting on a silent "thinking..."
        // state until a worker thread frees up.
        event.getHook().editOriginal(VERIFY_IN_PROGRESS_MESSAGE).setEmbeds(List.of()).setComponents(List.of()).queue();
        confirmExecutor.submit(() -> {
            try {
                VerificationCode verificationCode = verificationFacade.initiateAndNotify(
                        pending.discordId(), pending.guildId(), pending.aisId());

                eventLogEmbedSender.send(event.getGuild(), LogEventType.VERIFY_REQUESTED, new EmbedBuilder()
                        .setColor(new Color(0x6366F1))
                        .setTitle("Verification Requested")
                        .addField("User", "<@" + pending.discordId() + ">", true)
                        .addField("AIS ID", pending.aisId(), true));

                String message = verificationProperties.testingMode()
                        ? "**TESTING MODE** — email sending vypnuté. Tvoj kód: `" + verificationCode.getCode()
                                + "`\nPouži `/code " + verificationCode.getCode() + "`. Kód platí 15 minút."
                        : "Verifikačný email poslaný! Pozri si STUBA mail a potvrď kód cez `/code <kód>`. Kód platí 15 minút.";
                event.getHook().editOriginal(message).setEmbeds(List.of()).setComponents(List.of()).queue();
            } catch (DomainException e) {
                event.getHook().editOriginal(e.getMessage()).setEmbeds(List.of()).setComponents(List.of()).queue();
            } catch (Exception e) {
                log.error("Verify confirmation failed", e);
                event.getHook().editOriginal(GENERIC_ERROR_MESSAGE).setEmbeds(List.of()).setComponents(List.of()).queue();
            }
        });
    }
}
