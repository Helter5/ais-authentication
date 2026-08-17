package sk.gkanocz.aisauth.rolemenu;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.verification.MemberVerificationChecker;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleMenuInteractionListenerTest {

    @Mock
    private RoleMenuConfigRepository roleMenuConfigRepository;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private MemberVerificationChecker memberVerificationChecker;

    @Mock
    private Guild guild;
    @Mock
    private Member self;
    @Mock
    private Member member;

    private final RoleMenuService roleMenuService = new RoleMenuService(new ObjectMapper());
    private RoleMenuInteractionListener listener;

    @BeforeEach
    void setUp() {
        listener = new RoleMenuInteractionListener(
                roleMenuConfigRepository, roleMenuService, adminSettingsService, memberVerificationChecker);

        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(adminSettingsService.isMaintenanceMode()).thenReturn(false);
        Mockito.lenient().when(adminSettingsService.get("rolemenu_enabled_guild-1", Boolean.class, false)).thenReturn(true);
        Mockito.lenient().when(guild.getSelfMember()).thenReturn(self);
        Mockito.lenient().when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
    }

    private RoleMenuOption option(String roleId) {
        return new RoleMenuOption(roleId, "Label " + roleId, null, null);
    }

    private RoleMenuConfig config(String selectionMode, boolean requireVerified, Integer maxSelectable, RoleMenuOption... options) {
        RoleMenuConfig config = new RoleMenuConfig(
                "guild-1", "channel-1", "Pick a role", "desc", "BUTTONS", selectionMode, requireVerified,
                roleMenuService.writeOptions(List.of(options)),
                roleMenuService.writeRoleIds(List.of()), roleMenuService.writeRoleIds(List.of()), maxSelectable);
        org.springframework.test.util.ReflectionTestUtils.setField(config, "id", 1L);
        when(roleMenuConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        return config;
    }

    private Role role(String id, String name) {
        Role role = mock(Role.class);
        Mockito.lenient().when(role.getId()).thenReturn(id);
        Mockito.lenient().when(role.getName()).thenReturn(name);
        Mockito.lenient().when(guild.getRoleById(id)).thenReturn(role);
        Mockito.lenient().when(self.canInteract(role)).thenReturn(true);
        return role;
    }

    // ---- onButtonInteraction: routing guards ----

    @Test
    @SuppressWarnings("unchecked")
    void buttonIgnoresComponentIdsThatAreNotRoleMenuPrefixed() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("something_else:1:role-1");

        listener.onButtonInteraction(event);

        verify(roleMenuConfigRepository, never()).findById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonIgnoresMalformedComponentId() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("rolemenu:1");

        listener.onButtonInteraction(event);

        verify(roleMenuConfigRepository, never()).findById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonIgnoresNonNumericConfigId() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("rolemenu:abc:role-1");

        listener.onButtonInteraction(event);

        verify(roleMenuConfigRepository, never()).findById(any());
    }

    private ButtonInteractionEvent buttonEvent(String roleId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("rolemenu:1:" + roleId);
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(event.getMember()).thenReturn(member);
        return event;
    }

    @Test
    void buttonDoesNothingWhenGuildIsNull() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("rolemenu:1:role-1");
        when(event.getGuild()).thenReturn(null);

        listener.onButtonInteraction(event);

        verify(roleMenuConfigRepository, never()).findById(any());
    }

    @Test
    void buttonDoesNothingDuringMaintenanceMode() {
        when(adminSettingsService.isMaintenanceMode()).thenReturn(true);
        ButtonInteractionEvent event = buttonEvent("role-1");

        listener.onButtonInteraction(event);

        verify(roleMenuConfigRepository, never()).findById(any());
    }

    @Test
    void buttonDoesNothingWhenRoleMenuModuleDisabled() {
        when(adminSettingsService.get("rolemenu_enabled_guild-1", Boolean.class, false)).thenReturn(false);
        ButtonInteractionEvent event = buttonEvent("role-1");

        listener.onButtonInteraction(event);

        verify(roleMenuConfigRepository, never()).findById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonDoesNothingWhenConfigBelongsToADifferentGuild() {
        RoleMenuConfig other = new RoleMenuConfig(
                "other-guild", "channel-1", "Title", "desc", "BUTTONS", "SINGLE", false,
                roleMenuService.writeOptions(List.of(option("role-1"))),
                roleMenuService.writeRoleIds(List.of()), roleMenuService.writeRoleIds(List.of()), null);
        when(roleMenuConfigRepository.findById(1L)).thenReturn(Optional.of(other));
        ButtonInteractionEvent event = buttonEvent("role-1");

        listener.onButtonInteraction(event);

        verify(event, never()).reply(anyString());
    }

    // ---- onButtonInteraction: access checks ----

    @Test
    @SuppressWarnings("unchecked")
    void buttonRepliesGenericErrorWhenMemberIsNull() {
        config("SINGLE", false, null, option("role-1"));
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("rolemenu:1:role-1");
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(null);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("Something went wrong.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonDeniesWhenMemberHasABlockedRole() {
        RoleMenuConfig cfg = new RoleMenuConfig(
                "guild-1", "channel-1", "Title", "desc", "BUTTONS", "SINGLE", false,
                roleMenuService.writeOptions(List.of(option("role-1"))),
                roleMenuService.writeRoleIds(List.of()), roleMenuService.writeRoleIds(List.of("blocked-role")), null);
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "id", 1L);
        when(roleMenuConfigRepository.findById(1L)).thenReturn(Optional.of(cfg));
        Role blockedRole = role("blocked-role", "Blocked");
        when(member.getRoles()).thenReturn(List.of(blockedRole));
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("You're not allowed to use this role menu.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonDeniesWhenMemberLacksAnAllowedRole() {
        RoleMenuConfig cfg = new RoleMenuConfig(
                "guild-1", "channel-1", "Title", "desc", "BUTTONS", "SINGLE", false,
                roleMenuService.writeOptions(List.of(option("role-1"))),
                roleMenuService.writeRoleIds(List.of("required-role")), roleMenuService.writeRoleIds(List.of()), null);
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "id", 1L);
        when(roleMenuConfigRepository.findById(1L)).thenReturn(Optional.of(cfg));
        when(member.getRoles()).thenReturn(List.of());
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("You don't have permission to use this role menu.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonDeniesWhenVerificationIsRequiredAndMemberIsNotVerified() {
        config("SINGLE", true, null, option("role-1"));
        when(memberVerificationChecker.isVerified(guild, member)).thenReturn(false);
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("You need to verify first (/verify) before using this role menu.");
    }

    // ---- onButtonInteraction: role resolution ----

    @Test
    @SuppressWarnings("unchecked")
    void buttonRepliesWhenClickedRoleNoLongerExists() {
        config("SINGLE", false, null, option("role-1"));
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("That role no longer exists.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonRepliesWhenBotCannotManageTheClickedRole() {
        config("SINGLE", false, null, option("role-1"));
        Role clicked = role("role-1", "Role One");
        when(self.canInteract(clicked)).thenReturn(false);
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("I can't manage that role right now - contact a moderator.");
    }

    // ---- onButtonInteraction: toggle behavior ----

    @Test
    @SuppressWarnings("unchecked")
    void buttonAddsTheRoleWhenMemberDoesNotHaveIt() {
        config("SINGLE", false, null, option("role-1"));
        Role clicked = role("role-1", "Role One");
        when(member.getRoles()).thenReturn(List.of());
        AuditableRestAction<Void> addAction = mock(AuditableRestAction.class);
        when(guild.addRoleToMember(member, clicked)).thenReturn(addAction);
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(guild).addRoleToMember(member, clicked);
        verify(event).reply("Added: Role One");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonRemovesTheRoleWhenMemberAlreadyHasIt() {
        config("MULTI", false, null, option("role-1"));
        Role clicked = role("role-1", "Role One");
        when(member.getRoles()).thenReturn(List.of(clicked));
        AuditableRestAction<Void> removeAction = mock(AuditableRestAction.class);
        when(guild.removeRoleFromMember(member, clicked)).thenReturn(removeAction);
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(guild).removeRoleFromMember(member, clicked);
        verify(event).reply("Removed: Role One");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonSingleModeRemovesSiblingRoleBeforeAddingClickedRole() {
        config("SINGLE", false, null, option("role-1"), option("role-2"));
        Role clicked = role("role-1", "Role One");
        Role sibling = role("role-2", "Role Two");
        when(member.getRoles()).thenReturn(List.of(sibling));
        AuditableRestAction<Void> addAction = mock(AuditableRestAction.class);
        AuditableRestAction<Void> removeAction = mock(AuditableRestAction.class);
        when(guild.addRoleToMember(member, clicked)).thenReturn(addAction);
        when(guild.removeRoleFromMember(member, sibling)).thenReturn(removeAction);
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(guild).removeRoleFromMember(member, sibling);
        verify(guild).addRoleToMember(member, clicked);
        verify(event).reply("Added: Role One\nRemoved: Role Two");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonMultiModeRejectsWhenMaxSelectableReached() {
        config("MULTI", false, 1, option("role-1"), option("role-2"));
        Role clicked = role("role-1", "Role One");
        Role heldRole = role("role-2", "Role Two");
        when(member.getRoles()).thenReturn(List.of(heldRole));
        ButtonInteractionEvent event = buttonEvent("role-1");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("You can only have up to 1 role(s) from this menu - remove one first.");
        verify(guild, never()).addRoleToMember(any(), any());
    }

    // ---- onStringSelectInteraction ----

    @Test
    @SuppressWarnings("unchecked")
    void selectIgnoresComponentIdsThatAreNotRoleMenuPrefixed() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        when(event.getComponentId()).thenReturn("something_else:1");

        listener.onStringSelectInteraction(event);

        verify(roleMenuConfigRepository, never()).findById(any());
    }

    private StringSelectInteractionEvent selectEvent(List<String> values) {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        when(event.getComponentId()).thenReturn("rolemenu:1");
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(event.getMember()).thenReturn(member);
        Mockito.lenient().when(event.getValues()).thenReturn(values);
        return event;
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectRejectsWhenMoreThanMaxSelectableAreChosen() {
        config("MULTI", false, 1, option("role-1"), option("role-2"));
        StringSelectInteractionEvent event = selectEvent(List.of("role-1", "role-2"));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onStringSelectInteraction(event);

        verify(event).reply("You can select at most 1 role(s) from this menu.");
        verify(guild, never()).addRoleToMember(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectAddsAndRemovesRolesToMatchTheSelection() {
        config("MULTI", false, null, option("role-1"), option("role-2"));
        Role toAdd = role("role-1", "Role One");
        Role toRemove = role("role-2", "Role Two");
        when(member.getRoles()).thenReturn(List.of(toRemove));
        AuditableRestAction<Void> addAction = mock(AuditableRestAction.class);
        AuditableRestAction<Void> removeAction = mock(AuditableRestAction.class);
        when(guild.addRoleToMember(member, toAdd)).thenReturn(addAction);
        when(guild.removeRoleFromMember(member, toRemove)).thenReturn(removeAction);
        StringSelectInteractionEvent event = selectEvent(List.of("role-1"));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onStringSelectInteraction(event);

        verify(guild).addRoleToMember(member, toAdd);
        verify(guild).removeRoleFromMember(member, toRemove);
        verify(event).reply("Added: Role One\nRemoved: Role Two");
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectReportsNoChangesWhenSelectionAlreadyMatchesHeldRoles() {
        config("MULTI", false, null, option("role-1"));
        Role held = role("role-1", "Role One");
        when(member.getRoles()).thenReturn(List.of(held));
        StringSelectInteractionEvent event = selectEvent(List.of("role-1"));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onStringSelectInteraction(event);

        verify(event).reply("No changes.");
        verify(guild, never()).addRoleToMember(any(), any());
        verify(guild, never()).removeRoleFromMember(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectReportsBlockedRolesTheBotCannotManage() {
        config("MULTI", false, null, option("role-1"));
        Role clicked = role("role-1", "Role One");
        when(self.canInteract(clicked)).thenReturn(false);
        when(member.getRoles()).thenReturn(List.of());
        StringSelectInteractionEvent event = selectEvent(List.of("role-1"));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onStringSelectInteraction(event);

        verify(event).reply("No changes.\nCouldn't update: Role One (contact a moderator).");
        verify(guild, never()).addRoleToMember(any(), any());
    }
}
