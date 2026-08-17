package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuildAllowlistEventManagerTest {

    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private Guild guild;

    private GuildAllowlistEventManager manager;
    private boolean dispatched;

    @BeforeEach
    void setUp() {
        manager = new GuildAllowlistEventManager(adminSettingsService);
        dispatched = false;
        manager.register((EventListener) event -> dispatched = true);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
    }

    @Test
    void slashCommandsAlwaysDispatchRegardlessOfAllowlist() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);

        manager.handle(event);

        assertThat(dispatched).isTrue();
    }

    @Test
    void messageFromAnAllowedGuildDispatches() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(true);

        manager.handle(event);

        assertThat(dispatched).isTrue();
    }

    @Test
    void messageFromADisallowedGuildIsDropped() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(false);

        manager.handle(event);

        assertThat(dispatched).isFalse();
    }

    @Test
    void messageNotFromAGuildDispatchesWithoutCheckingTheAllowlist() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.isFromGuild()).thenReturn(false);

        manager.handle(event);

        assertThat(dispatched).isTrue();
        verify(adminSettingsService, Mockito.never()).isGuildAllowed(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void buttonInteractionOutsideAGuildDispatches() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getGuild()).thenReturn(null);

        manager.handle(event);

        assertThat(dispatched).isTrue();
    }

    @Test
    void buttonInteractionInADisallowedGuildIsDropped() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(false);

        manager.handle(event);

        assertThat(dispatched).isFalse();
    }

    @Test
    void stringSelectInteractionInAnAllowedGuildDispatches() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(true);

        manager.handle(event);

        assertThat(dispatched).isTrue();
    }

    @Test
    void stringSelectInteractionInADisallowedGuildIsDropped() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(false);

        manager.handle(event);

        assertThat(dispatched).isFalse();
    }

    @Test
    void unrecognizedEventTypesAlwaysDispatch() {
        GenericEvent event = mock(GenericEvent.class);

        manager.handle(event);

        assertThat(dispatched).isTrue();
    }

    @Test
    void getRegisteredListenersIncludesTheRegisteredListener() {
        assertThat(manager.getRegisteredListeners()).hasSize(1);
    }

    @Test
    void unregisterRemovesTheListenerSoItStopsReceivingEvents() {
        new java.util.ArrayList<>(manager.getRegisteredListeners()).forEach(manager::unregister);

        manager.handle(mock(SlashCommandInteractionEvent.class));

        assertThat(dispatched).isFalse();
    }
}
