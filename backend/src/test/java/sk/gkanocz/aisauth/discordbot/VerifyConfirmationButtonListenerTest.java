package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import sk.gkanocz.aisauth.verification.VerificationCode;
import sk.gkanocz.aisauth.verification.VerificationFacade;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyConfirmationButtonListenerTest {

    @Mock
    private PendingVerificationStore pendingVerificationStore;
    @Mock
    private VerificationFacade verificationFacade;
    @Mock
    private EventLogEmbedSender eventLogEmbedSender;

    @Mock
    private ButtonInteractionEvent event;
    @Mock
    private Guild guild;
    @Mock
    private User user;
    @Mock
    private InteractionHook hook;

    private VerifyConfirmationButtonListener listener;

    @BeforeEach
    void setUp() {
        listener = new VerifyConfirmationButtonListener(
                pendingVerificationStore, verificationFacade, eventLogEmbedSender,
                new VerificationProperties(List.of(), "Student", false));

        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(event.getUser()).thenReturn(user);
        Mockito.lenient().when(user.getId()).thenReturn("discord-1");
        Mockito.lenient().when(event.getHook()).thenReturn(hook);
    }

    private PendingVerificationStore.Pending pending() {
        return new PendingVerificationStore.Pending("discord-1", "guild-1", "123456");
    }

    @Test
    void ignoresComponentIdsThatAreNotVerifyConfirmationButtons() {
        when(event.getComponentId()).thenReturn("something_else:token-1");

        listener.onButtonInteraction(event);

        verify(pendingVerificationStore, never()).get(anyString());
    }

    @Test
    void ignoresInteractionsOutsideAGuild() {
        when(event.getComponentId()).thenReturn("verify_confirm:token-1");
        when(event.getGuild()).thenReturn(null);

        listener.onButtonInteraction(event);

        verify(pendingVerificationStore, never()).get(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void editsExpiredMessageWhenTokenIsUnknownOrExpired() {
        when(event.getComponentId()).thenReturn("verify_confirm:token-1");
        when(pendingVerificationStore.get("token-1")).thenReturn(Optional.empty());
        MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.editMessage(anyString())).thenReturn(editAction);

        listener.onButtonInteraction(event);

        verify(event).editMessage("Táto žiadosť už expirovala. Spusti `/verify` znova.");
        verify(editAction).setEmbeds(List.of());
        verify(editAction).setComponents(List.of());
        verify(pendingVerificationStore, never()).remove(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void repliesEphemeralWhenTokenBelongsToADifferentUser() {
        when(event.getComponentId()).thenReturn("verify_confirm:token-1");
        when(pendingVerificationStore.get("token-1")).thenReturn(Optional.of(
                new PendingVerificationStore.Pending("other-user", "guild-1", "123456")));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("Toto nie je tvoja verifikácia.");
        verify(replyAction).setEphemeral(true);
        verify(pendingVerificationStore, never()).remove(anyString());
        verify(verificationFacade, never()).initiateAndNotify(anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelRemovesThePendingEntryAndEditsTheMessage() {
        when(event.getComponentId()).thenReturn("verify_cancel:token-1");
        when(pendingVerificationStore.get("token-1")).thenReturn(Optional.of(pending()));
        MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.editMessage(anyString())).thenReturn(editAction);

        listener.onButtonInteraction(event);

        verify(pendingVerificationStore).remove("token-1");
        verify(event).editMessage("Verifikácia zrušená.");
        verify(verificationFacade, never()).initiateAndNotify(anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmSendsCodeAndEditsHookWithProductionMessage() {
        when(event.getComponentId()).thenReturn("verify_confirm:token-1");
        when(pendingVerificationStore.get("token-1")).thenReturn(Optional.of(pending()));
        when(event.deferEdit()).thenReturn(mock(MessageEditCallbackAction.class));
        VerificationCode code = new VerificationCode(
                "discord-1", "guild-1", "654321", "student@stuba.sk", "123456", LocalDateTime.now().plusMinutes(15));
        when(verificationFacade.initiateAndNotify("discord-1", "guild-1", "123456")).thenReturn(code);
        WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> hookEdit =
                mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        when(hook.editOriginal(anyString())).thenReturn(hookEdit);

        listener.onButtonInteraction(event);

        verify(pendingVerificationStore).remove("token-1");
        verify(eventLogEmbedSender).send(any(Guild.class), any(), any());
        verify(hook).editOriginal("Verifikačný email poslaný! Pozri si STUBA mail a potvrď kód cez `/code <kód>`. Kód platí 15 minút.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmInTestingModeShowsCodeDirectly() {
        listener = new VerifyConfirmationButtonListener(
                pendingVerificationStore, verificationFacade, eventLogEmbedSender,
                new VerificationProperties(List.of(), "Student", true));
        when(event.getComponentId()).thenReturn("verify_confirm:token-1");
        when(pendingVerificationStore.get("token-1")).thenReturn(Optional.of(pending()));
        when(event.deferEdit()).thenReturn(mock(MessageEditCallbackAction.class));
        VerificationCode code = new VerificationCode(
                "discord-1", "guild-1", "654321", "student@stuba.sk", "123456", LocalDateTime.now().plusMinutes(15));
        when(verificationFacade.initiateAndNotify("discord-1", "guild-1", "123456")).thenReturn(code);
        WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> hookEdit =
                mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        when(hook.editOriginal(anyString())).thenReturn(hookEdit);

        listener.onButtonInteraction(event);

        verify(hook).editOriginal("**TESTING MODE** — email sending vypnuté. Tvoj kód: `654321`\nPouži `/code 654321`. Kód platí 15 minút.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmEditsHookWithDomainExceptionMessageOnFailure() {
        when(event.getComponentId()).thenReturn("verify_confirm:token-1");
        when(pendingVerificationStore.get("token-1")).thenReturn(Optional.of(pending()));
        when(event.deferEdit()).thenReturn(mock(MessageEditCallbackAction.class));
        when(verificationFacade.initiateAndNotify("discord-1", "guild-1", "123456"))
                .thenThrow(InvalidRequestException.withMessage("Invalid AIS ID"));
        WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> hookEdit =
                mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        when(hook.editOriginal(anyString())).thenReturn(hookEdit);

        listener.onButtonInteraction(event);

        verify(hook).editOriginal("Invalid AIS ID");
        verify(eventLogEmbedSender, never()).send(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmEditsHookWithGenericMessageOnUnexpectedFailure() {
        when(event.getComponentId()).thenReturn("verify_confirm:token-1");
        when(pendingVerificationStore.get("token-1")).thenReturn(Optional.of(pending()));
        when(event.deferEdit()).thenReturn(mock(MessageEditCallbackAction.class));
        when(verificationFacade.initiateAndNotify("discord-1", "guild-1", "123456"))
                .thenThrow(new RuntimeException("boom"));
        WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> hookEdit =
                mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        when(hook.editOriginal(anyString())).thenReturn(hookEdit);

        listener.onButtonInteraction(event);

        verify(hook).editOriginal("Nastala neočakávaná chyba, skús to prosím neskôr.");
    }
}
