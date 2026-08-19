package sk.gkanocz.aisauth.rolemenu;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleMenuServiceTest {

    @Mock
    private Guild guild;
    @Mock
    private TextChannel channel;

    private RoleMenuService service;

    @BeforeEach
    void setUp() {
        service = new RoleMenuService(new ObjectMapper());
    }

    private RoleMenuOption option(String roleId) {
        return new RoleMenuOption(List.of(roleId), "Label " + roleId, null, null);
    }

    private RoleMenuConfig buttonConfig(String channelId) {
        return new RoleMenuConfig(
                "guild-1", channelId, null, "Pick a role", "desc", "BUTTONS", "NORMAL", false,
                service.writeOptions(List.of(option("role-1"), option("role-2"))),
                service.writeRoleIds(List.of()), service.writeRoleIds(List.of()), null);
    }

    // ---- option/role-id JSON round trips ----

    @Test
    void writeOptionsRejectsAnEmptyList() {
        assertThatThrownBy(() -> service.writeOptions(List.of()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at least one role");
    }

    @Test
    void writeOptionsRejectsNull() {
        assertThatThrownBy(() -> service.writeOptions(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void writeOptionsRejectsMoreThanTwentyFiveRoles() {
        List<RoleMenuOption> tooMany = java.util.stream.IntStream.range(0, 26)
                .mapToObj(i -> option("role-" + i)).toList();

        assertThatThrownBy(() -> service.writeOptions(tooMany))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at most 25");
    }

    @Test
    void optionsRoundTripThroughJson() {
        List<RoleMenuOption> original = List.of(option("role-1"), new RoleMenuOption(List.of("role-2"), "Two", "🎉", "desc"));

        String json = service.writeOptions(original);
        List<RoleMenuOption> readBack = service.readOptions(json);

        assertThat(readBack).isEqualTo(original);
    }

    @Test
    void writeOptionsRejectsAnOptionWithNoRoles() {
        assertThatThrownBy(() -> service.writeOptions(List.of(new RoleMenuOption(List.of(), "Empty", null, null))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at least one Discord role");
    }

    @Test
    void optionsWithMultipleBundledRolesRoundTripThroughJson() {
        List<RoleMenuOption> original = List.of(new RoleMenuOption(List.of("role-1", "role-2"), "2. ROCNIK + API", null, null));

        String json = service.writeOptions(original);
        List<RoleMenuOption> readBack = service.readOptions(json);

        assertThat(readBack).isEqualTo(original);
    }

    @Test
    void readOptionsAcceptsTheLegacySingleRoleIdShape() {
        String legacyJson = "[{\"role_id\":\"role-1\",\"label\":\"One\",\"emoji\":null,\"description\":null}]";

        List<RoleMenuOption> readBack = service.readOptions(legacyJson);

        assertThat(readBack).containsExactly(new RoleMenuOption(List.of("role-1"), "One", null, null));
    }

    @Test
    void roleIdsRoundTripThroughJson() {
        List<String> ids = List.of("role-1", "role-2");

        assertThat(service.readRoleIds(service.writeRoleIds(ids))).isEqualTo(ids);
    }

    @Test
    void writeRoleIdsTreatsNullAsEmptyList() {
        assertThat(service.readRoleIds(service.writeRoleIds(null))).isEmpty();
    }

    // ---- postOrUpdateMenu ----

    @Test
    void postOrUpdateMenuThrowsWhenChannelNotFound() {
        RoleMenuConfig config = buttonConfig("channel-1");
        when(guild.getTextChannelById("channel-1")).thenReturn(null);

        assertThatThrownBy(() -> service.postOrUpdateMenu(guild, config, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Channel not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void postOrUpdateMenuPostsFreshWhenNoPreviousMessage() {
        RoleMenuConfig config = buttonConfig("channel-1");
        when(guild.getTextChannelById("channel-1")).thenReturn(channel);
        MessageCreateAction createAction = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        Message created = mock(Message.class);
        when(created.getId()).thenReturn("message-1");
        when(createAction.complete()).thenReturn(created);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(createAction);

        service.postOrUpdateMenu(guild, config, null, null);

        assertThat(config.getMessageId()).isEqualTo("message-1");
        verify(channel, never()).editMessageEmbedsById(anyString(), any(MessageEmbed.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void postOrUpdateMenuEditsExistingMessageInSameChannel() {
        RoleMenuConfig config = buttonConfig("channel-1");
        when(guild.getTextChannelById("channel-1")).thenReturn(channel);
        MessageEditAction editAction = mock(MessageEditAction.class, Mockito.RETURNS_SELF);
        when(editAction.complete()).thenReturn(mock(Message.class));
        when(channel.editMessageEmbedsById(anyString(), any(MessageEmbed.class))).thenReturn(editAction);

        service.postOrUpdateMenu(guild, config, "channel-1", "message-1");

        verify(channel).editMessageEmbedsById(org.mockito.ArgumentMatchers.eq("message-1"), any(MessageEmbed.class));
        verify(channel, never()).sendMessageEmbeds(any(MessageEmbed.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void postOrUpdateMenuFallsBackToFreshPostWhenEditFails() {
        RoleMenuConfig config = buttonConfig("channel-1");
        when(guild.getTextChannelById("channel-1")).thenReturn(channel);
        MessageEditAction editAction = mock(MessageEditAction.class, Mockito.RETURNS_SELF);
        when(editAction.complete()).thenThrow(new RuntimeException("message deleted"));
        when(channel.editMessageEmbedsById(anyString(), any(MessageEmbed.class))).thenReturn(editAction);

        MessageCreateAction createAction = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        Message created = mock(Message.class);
        when(created.getId()).thenReturn("message-2");
        when(createAction.complete()).thenReturn(created);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(createAction);

        service.postOrUpdateMenu(guild, config, "channel-1", "message-1");

        assertThat(config.getMessageId()).isEqualTo("message-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void postOrUpdateMenuDeletesOldMessageAndPostsFreshWhenChannelChanged() {
        RoleMenuConfig config = buttonConfig("channel-2");
        TextChannel oldChannel = mock(TextChannel.class);
        when(guild.getTextChannelById("channel-2")).thenReturn(channel);
        when(guild.getTextChannelById("channel-1")).thenReturn(oldChannel);
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(oldChannel.deleteMessageById("message-1")).thenReturn(deleteAction);

        MessageCreateAction createAction = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        Message created = mock(Message.class);
        when(created.getId()).thenReturn("message-2");
        when(createAction.complete()).thenReturn(created);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(createAction);

        service.postOrUpdateMenu(guild, config, "channel-1", "message-1");

        verify(deleteAction).queue(any(), any());
        assertThat(config.getMessageId()).isEqualTo("message-2");
        verify(channel, never()).editMessageEmbedsById(anyString(), any(MessageEmbed.class));
    }

    // ---- deleteMenuMessage ----

    @Test
    void deleteMenuMessageIsNoOpWhenNoMessageWasEverPosted() {
        RoleMenuConfig config = buttonConfig("channel-1");

        service.deleteMenuMessage(guild, config);

        verify(guild, never()).getTextChannelById(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteMenuMessageDeletesWhenMessageExists() {
        RoleMenuConfig config = buttonConfig("channel-1");
        when(guild.getTextChannelById("channel-1")).thenReturn(channel);
        MessageCreateAction createAction = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        Message created = mock(Message.class);
        when(created.getId()).thenReturn("message-1");
        when(createAction.complete()).thenReturn(created);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(createAction);
        service.postOrUpdateMenu(guild, config, null, null);

        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(channel.deleteMessageById("message-1")).thenReturn(deleteAction);

        service.deleteMenuMessage(guild, config);

        verify(deleteAction).queue(any(), any());
    }
}
