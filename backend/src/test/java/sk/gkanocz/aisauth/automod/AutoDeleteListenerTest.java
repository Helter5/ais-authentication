package sk.gkanocz.aisauth.automod;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers only the guard clauses in onMessageReceived (does auto-delete apply at all) - the actual
 * scheduled delete/notify pipeline (performDelete/notifyIfConfigured) runs on a real background
 * ScheduledExecutorService with a deep JDA action-chain and is left uncovered here, same precedent
 * as HackedAccountTrapListenerTest. The immediate (delaySeconds == 0) path is covered up to the
 * point where the message retrieval RestAction is kicked off, since that runs synchronously.
 */
@ExtendWith(MockitoExtension.class)
class AutoDeleteListenerTest {

    @Mock
    private AutoDeleteConfigRepository autoDeleteConfigRepository;
    @Mock
    private AdminSettingsService adminSettingsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MessageReceivedEvent event;
    @Mock
    private Guild guild;
    @Mock
    private User author;
    @Mock
    private MessageChannelUnion channelUnion;

    private AutoDeleteListener listener;

    @BeforeEach
    void setUp() {
        listener = new AutoDeleteListener(autoDeleteConfigRepository, adminSettingsService, objectMapper);
    }

    @Test
    void ignoresMessagesNotFromGuild() {
        when(event.isFromGuild()).thenReturn(false);

        listener.onMessageReceived(event);

        verifyNoInteractions(adminSettingsService, autoDeleteConfigRepository);
    }

    @Test
    void skipsDuringMaintenanceMode() {
        when(event.isFromGuild()).thenReturn(true);
        when(adminSettingsService.isMaintenanceMode()).thenReturn(true);

        listener.onMessageReceived(event);

        verifyNoInteractions(autoDeleteConfigRepository);
    }

    private void stubEnabledCheck(boolean enabled) {
        when(event.isFromGuild()).thenReturn(true);
        when(adminSettingsService.isMaintenanceMode()).thenReturn(false);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild-1");
        when(adminSettingsService.get("autodelete_enabled_guild-1", Boolean.class, false)).thenReturn(enabled);
    }

    @Test
    void skipsWhenFeatureDisabledForGuild() {
        stubEnabledCheck(false);

        listener.onMessageReceived(event);

        verifyNoInteractions(autoDeleteConfigRepository);
    }

    @Test
    void skipsWhenNoConfigExistsForChannel() {
        stubEnabledCheck(true);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.getId()).thenReturn("channel-1");
        when(autoDeleteConfigRepository.findByGuildIdAndChannelId("guild-1", "channel-1")).thenReturn(List.of());

        listener.onMessageReceived(event);

        Mockito.verifyNoMoreInteractions(channelUnion);
    }

    private AutoDeleteConfig config(boolean ignoreBots, String ignoreRoleIds, String ignoreUserIds) {
        return new AutoDeleteConfig("guild-1", "channel-1", 0, ignoreRoleIds, ignoreUserIds,
                ignoreBots, false, false, "channel", "msg", false, 0);
    }

    private void stubConfigLookup(AutoDeleteConfig config) {
        stubEnabledCheck(true);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.getId()).thenReturn("channel-1");
        when(autoDeleteConfigRepository.findByGuildIdAndChannelId("guild-1", "channel-1")).thenReturn(List.of(config));
    }

    @Test
    void skipsWhenIgnoreBotsAndAuthorIsBot() {
        stubConfigLookup(config(true, "[]", "[]"));
        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(true);

        listener.onMessageReceived(event);

        Mockito.verify(channelUnion, never()).asGuildMessageChannel();
    }

    @Test
    void skipsWhenAuthorIsInIgnoreUserIdsList() {
        stubConfigLookup(config(false, "[]", "[\"user-1\"]"));
        when(event.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn("user-1");

        listener.onMessageReceived(event);

        Mockito.verify(channelUnion, never()).asGuildMessageChannel();
    }

    @Test
    void skipsWhenMemberHoldsAnIgnoredRole() {
        stubConfigLookup(config(false, "[\"role-9\"]", "[]"));
        when(event.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn("user-1");
        Member member = mock(Member.class);
        Role role = mock(Role.class);
        when(role.getId()).thenReturn("role-9");
        when(member.getRoles()).thenReturn(List.of(role));
        when(event.getMember()).thenReturn(member);

        listener.onMessageReceived(event);

        Mockito.verify(channelUnion, never()).asGuildMessageChannel();
    }

    @SuppressWarnings("unchecked")
    @Test
    void immediatelyRetrievesTheMessageWhenNoGuardBlocksAndDelayIsZero() {
        stubConfigLookup(config(false, "[]", "[]"));
        when(event.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn("user-1");
        when(event.getMember()).thenReturn(null);
        GuildMessageChannel guildChannel = mock(GuildMessageChannel.class);
        when(channelUnion.asGuildMessageChannel()).thenReturn(guildChannel);
        when(event.getMessageId()).thenReturn("msg-1");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getName()).thenReturn("My Guild");
        RestAction<net.dv8tion.jda.api.entities.Message> action = mock(RestAction.class);
        when(guildChannel.retrieveMessageById("msg-1")).thenReturn(action);

        listener.onMessageReceived(event);

        verify(guildChannel).retrieveMessageById("msg-1");
        verify(action).queue(any(), any());
    }
}
