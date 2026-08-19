package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqSlashCommandListenerTest {

    @Mock
    private AdminSettingsService adminSettingsService;

    @Mock
    private SlashCommandInteractionEvent event;
    @Mock
    private Guild guild;

    private FaqSlashCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new FaqSlashCommandListener(adminSettingsService);
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
    }

    @Test
    void ignoresOtherCommandNames() {
        when(event.getName()).thenReturn("warn");

        listener.dispatch(event, null);

        verify(event, never()).replyEmbeds(any(MessageEmbed.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void repliesEphemerallyWithTheGuardMessageWhenNoCustomMessageIsConfigured() {
        when(event.getName()).thenReturn("faq");
        when(adminSettingsService.get(eq("cmd_settings_guild-1_faq"), any(TypeReference.class), any()))
                .thenReturn(Map.of());
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        when(event.replyEmbeds(embedCaptor.capture())).thenReturn(replyAction);

        listener.dispatch(event, null);

        verify(replyAction).setEphemeral(true);
        assertThat(embedCaptor.getValue().getDescription()).isEqualTo("FAQ nie je nastavené, kontaktuj administrátora.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void repliesWithTheConfiguredMessageAndResolvesChannelTokens() {
        when(event.getName()).thenReturn("faq");
        when(adminSettingsService.get(eq("cmd_settings_guild-1_faq"), any(TypeReference.class), any()))
                .thenReturn(Map.of("message", "Pozri aj {channel=111111111111111111}."));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        when(event.replyEmbeds(embedCaptor.capture())).thenReturn(replyAction);

        listener.dispatch(event, null);

        assertThat(embedCaptor.getValue().getDescription()).isEqualTo("Pozri aj <#111111111111111111>.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsToEphemeralWhenNoOverrideIsConfigured() {
        when(event.getName()).thenReturn("faq");
        when(adminSettingsService.get(eq("cmd_settings_guild-1_faq"), any(TypeReference.class), any()))
                .thenReturn(Map.of());
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);

        listener.dispatch(event, null);

        verify(replyAction).setEphemeral(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void honorsTheDashboardEphemeralOverride() {
        when(event.getName()).thenReturn("faq");
        when(adminSettingsService.get(eq("cmd_settings_guild-1_faq"), any(TypeReference.class), any()))
                .thenReturn(Map.of());
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);

        listener.dispatch(event, false);

        verify(replyAction).setEphemeral(false);
    }
}
