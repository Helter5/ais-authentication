package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventLogEmbedSenderTest {

    @Mock
    private LogRoutingService logRoutingService;
    @Mock
    private Guild guild;
    @Mock
    private TextChannel channel;

    private EventLogEmbedSender sender;

    @BeforeEach
    void setUp() {
        sender = new EventLogEmbedSender(logRoutingService);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
    }

    // ---- resolveChannel ----

    @Test
    void resolveChannelReturnsNullWhenNoChannelConfigured() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).thenReturn(Optional.empty());

        assertThat(sender.resolveChannel(guild, LogEventType.WIPE_RECAP)).isNull();
    }

    @Test
    void resolveChannelReturnsTheConfiguredChannel() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).thenReturn(Optional.of("chan-1"));
        when(guild.getTextChannelById("chan-1")).thenReturn(channel);

        assertThat(sender.resolveChannel(guild, LogEventType.WIPE_RECAP)).isSameAs(channel);
    }

    // ---- send ----

    @Test
    void sendReturnsFalseWhenNoChannelConfigured() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).thenReturn(Optional.empty());

        boolean result = sender.send(guild, LogEventType.WIPE_RECAP, new EmbedBuilder().setTitle("Test"));

        assertThat(result).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendPostsTheEmbedWhenChannelIsConfigured() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).thenReturn(Optional.of("chan-1"));
        when(guild.getTextChannelById("chan-1")).thenReturn(channel);
        MessageCreateAction action = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        when(channel.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(action);

        boolean result = sender.send(guild, LogEventType.WIPE_RECAP, new EmbedBuilder().setTitle("Test"));

        assertThat(result).isTrue();
        verify(action, never()).addActionRow(any(Button.class));
        verify(action).queue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendAddsTheLinkButtonWhenProvided() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WIPE_RECAP)).thenReturn(Optional.of("chan-1"));
        when(guild.getTextChannelById("chan-1")).thenReturn(channel);
        MessageCreateAction action = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        when(channel.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(action);
        Button link = Button.link("https://example.com", "View");

        boolean result = sender.send(guild, LogEventType.WIPE_RECAP, new EmbedBuilder().setTitle("Test"), link);

        assertThat(result).isTrue();
        verify(action).addActionRow(link);
    }

    // ---- sendToChannel ----

    @Test
    @SuppressWarnings("unchecked")
    void sendToChannelReturnsFalseWhenSendingThrows() {
        when(channel.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class)))
                .thenThrow(new RuntimeException("channel deleted"));
        when(channel.getId()).thenReturn("chan-1");

        boolean result = sender.sendToChannel(channel, new EmbedBuilder().setTitle("Test"), null);

        assertThat(result).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendToChannelReturnsTrueOnSuccessWithoutButton() {
        MessageCreateAction action = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        when(channel.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(action);

        boolean result = sender.sendToChannel(channel, new EmbedBuilder().setTitle("Test"), null);

        assertThat(result).isTrue();
        verify(action, never()).addActionRow(any(Button.class));
    }
}
