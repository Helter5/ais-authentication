package sk.gkanocz.aisauth.automod;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoMentionListenerTest {

    @Mock
    private AutoMentionRepository autoMentionRepository;
    @Mock
    private AdminSettingsService adminSettingsService;

    @Mock
    private MessageReceivedEvent event;
    @Mock
    private Guild guild;
    @Mock
    private User author;
    @Mock
    private MessageChannelUnion channelUnion;

    private AutoMentionListener listener;

    @BeforeEach
    void setUp() {
        listener = new AutoMentionListener(autoMentionRepository, adminSettingsService);
    }

    @Test
    void ignoresMessagesNotFromGuild() {
        when(event.isFromGuild()).thenReturn(false);

        listener.onMessageReceived(event);

        verifyNoInteractions(adminSettingsService, autoMentionRepository);
    }

    @Test
    void ignoresMessagesFromBots() {
        when(event.isFromGuild()).thenReturn(true);
        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(true);

        listener.onMessageReceived(event);

        verifyNoInteractions(adminSettingsService, autoMentionRepository);
    }

    private void stubPastAuthorGuard() {
        when(event.isFromGuild()).thenReturn(true);
        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(false);
    }

    @Test
    void skipsDuringMaintenanceMode() {
        stubPastAuthorGuard();
        when(adminSettingsService.isMaintenanceMode()).thenReturn(true);

        listener.onMessageReceived(event);

        verifyNoInteractions(autoMentionRepository);
    }

    private void stubEnabledCheck(boolean enabled) {
        stubPastAuthorGuard();
        when(adminSettingsService.isMaintenanceMode()).thenReturn(false);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild-1");
        when(adminSettingsService.get("automentions_enabled_guild-1", Boolean.class, false)).thenReturn(enabled);
    }

    @Test
    void skipsWhenFeatureDisabledForGuild() {
        stubEnabledCheck(false);

        listener.onMessageReceived(event);

        verifyNoInteractions(autoMentionRepository);
    }

    private void stubChannelLookup(Optional<AutoMention> found) {
        stubEnabledCheck(true);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.getId()).thenReturn("channel-1");
        when(autoMentionRepository.findByGuildIdAndChannelId("guild-1", "channel-1")).thenReturn(found);
    }

    @Test
    void doesNothingWhenNoMentionConfiguredForChannel() {
        stubChannelLookup(Optional.empty());

        listener.onMessageReceived(event);

        Mockito.verify(channelUnion, never()).asGuildMessageChannel();
    }

    @Test
    void doesNothingWhenMentionIsDisabled() {
        AutoMention mention = new AutoMention("guild-1", "channel-1", "role-1");
        mention.update("channel-1", "role-1", false, null);
        stubChannelLookup(Optional.of(mention));

        listener.onMessageReceived(event);

        Mockito.verify(channelUnion, never()).asGuildMessageChannel();
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendsRoleMentionWhenEnabledAndDoesNotScheduleDeleteWithoutADelay() {
        AutoMention mention = new AutoMention("guild-1", "channel-1", "role-1");
        stubChannelLookup(Optional.of(mention));
        GuildMessageChannel guildChannel = mock(GuildMessageChannel.class);
        when(channelUnion.asGuildMessageChannel()).thenReturn(guildChannel);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(guildChannel.sendMessage("<@&role-1>")).thenReturn(action);

        listener.onMessageReceived(event);

        ArgumentCaptor<Consumer<Message>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(action).queue(successCaptor.capture(), any());
        Message sentMessage = mock(Message.class);
        successCaptor.getValue().accept(sentMessage);

        verify(sentMessage, never()).delete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void schedulesDeleteWhenDeleteAfterSecondsIsConfigured() {
        AutoMention mention = new AutoMention("guild-1", "channel-1", "role-1");
        mention.update("channel-1", "role-1", true, 30);
        stubChannelLookup(Optional.of(mention));
        GuildMessageChannel guildChannel = mock(GuildMessageChannel.class);
        when(channelUnion.asGuildMessageChannel()).thenReturn(guildChannel);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(guildChannel.sendMessage("<@&role-1>")).thenReturn(action);

        listener.onMessageReceived(event);

        ArgumentCaptor<Consumer<Message>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(action).queue(successCaptor.capture(), any());
        Message sentMessage = mock(Message.class);
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(sentMessage.delete()).thenReturn(deleteAction);

        successCaptor.getValue().accept(sentMessage);

        verify(deleteAction).queueAfter(org.mockito.ArgumentMatchers.eq(30L),
                any(java.util.concurrent.TimeUnit.class), any(Consumer.class), any(Consumer.class));
    }
}
