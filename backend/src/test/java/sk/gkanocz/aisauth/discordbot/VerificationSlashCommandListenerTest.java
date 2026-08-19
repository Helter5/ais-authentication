package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.verification.AlreadyVerifiedException;
import sk.gkanocz.aisauth.verification.InvalidVerificationCodeException;
import sk.gkanocz.aisauth.verification.VerificationService;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationSlashCommandListenerTest {

    private static final VerificationProperties TESTING_MODE_OFF =
            new VerificationProperties(List.of("fei-stud"), "student:active", false);
    /** id handed back by the "⏳ Overujem..." progress message's send callback (see stubTextReply) -
     * handleVerify targets it with editMessageById(...) for every later outcome. */
    private static final String PROGRESS_MESSAGE_ID = "progress-msg-1";

    @Mock
    private VerificationService verificationService;
    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private LogRoutingService logRoutingService;
    @Mock
    private VerifiedRoleResolver verifiedRoleResolver;
    @Mock
    private VerifyRateLimiter verifyRateLimiter;
    @Mock
    private EventLogEmbedSender eventLogEmbedSender;
    @Mock
    private PendingVerificationStore pendingVerificationStore;
    @Mock
    private AdminSettingsService adminSettingsService;

    @Mock
    private SlashCommandInteractionEvent event;
    @Mock
    private Guild guild;
    @Mock
    private User user;
    @Mock
    private InteractionHook hook;
    @Mock
    private ReplyCallbackAction replyCallbackAction;
    @Mock
    private MessageChannelUnion channel;

    private VerificationSlashCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new VerificationSlashCommandListener(
                verificationService, guildSettingsService, logRoutingService, verifiedRoleResolver,
                verifyRateLimiter, eventLogEmbedSender, TESTING_MODE_OFF, pendingVerificationStore,
                adminSettingsService);

        Mockito.lenient().when(event.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        Mockito.lenient().when(event.getHook()).thenReturn(hook);
        Mockito.lenient().when(event.getUser()).thenReturn(user);
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(event.getName()).thenReturn("verify");
        Mockito.lenient().when(user.getId()).thenReturn("discord-1");
        Mockito.lenient().when(user.getName()).thenReturn("TestUser");
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(guild.getName()).thenReturn("TestGuild");
        Mockito.lenient().when(event.getChannel()).thenReturn(channel);
        Mockito.lenient().when(channel.getId()).thenReturn("channel-1");
        Mockito.lenient().when(channel.getName()).thenReturn("general");
        Mockito.lenient().when(adminSettingsService.get(anyString(), any(TypeReference.class), any()))
                .thenReturn(Map.of());
        Mockito.lenient().when(logRoutingService.channelIdFor(anyString(), any(LogEventType.class)))
                .thenReturn(Optional.of("channel-1"));
    }

    @SuppressWarnings("unchecked")
    private WebhookMessageCreateAction<Message> stubTextReply() {
        WebhookMessageCreateAction<Message> action = mock(WebhookMessageCreateAction.class, Mockito.RETURNS_SELF);
        Mockito.lenient().when(hook.sendMessage(anyString())).thenReturn(action);
        // /verify's "⏳ Overujem..." progress message is sent with .complete() (see
        // VerificationSlashCommandListener.handleVerify - blocking, not queue(callback), to avoid
        // racing editMessageById(...) against a not-yet-assigned id) so its id can later be used
        // to edit it in place - mimic that here.
        Message message = mock(Message.class);
        Mockito.lenient().when(message.getId()).thenReturn(PROGRESS_MESSAGE_ID);
        Mockito.lenient().when(action.complete()).thenReturn(message);
        return action;
    }

    @SuppressWarnings("unchecked")
    private WebhookMessageEditAction<Message> stubEditMessage() {
        WebhookMessageEditAction<Message> action = mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        Mockito.lenient().when(hook.editMessageById(anyString(), anyString())).thenReturn(action);
        return action;
    }

    /** Self-contained: never call this while another when(...) stub is still open on the same line. */
    private void stubStringOption(String name, String value) {
        OptionMapping option = mock(OptionMapping.class);
        Mockito.lenient().when(option.getAsString()).thenReturn(value);
        Mockito.lenient().when(event.getOption(name)).thenReturn(option);
    }

    private void stubMemberOption(String name, Member member) {
        OptionMapping option = mock(OptionMapping.class);
        Mockito.lenient().when(option.getAsMember()).thenReturn(member);
        Mockito.lenient().when(event.getOption(name)).thenReturn(option);
    }

    private void enableVerification() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(new GuildSettings("guild-1"));
    }

    private void disableVerification() {
        GuildSettings disabled = new GuildSettings("guild-1");
        disabled.setVerificationEnabled(false);
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(disabled);
    }

    private void configureLogChannel() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.VERIFY_REQUESTED))
                .thenReturn(Optional.of("channel-1"));
    }

    private void asCommand(String name) {
        Mockito.lenient().when(event.getName()).thenReturn(name);
    }

    // ---- /verify ----

    @Test
    void verifyRepliesWithDisabledMessageWhenVerificationIsDisabledForTheGuild() {
        disableVerification();
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(contains("vypnutá"));
        verify(verifiedRoleResolver, never()).resolveAssignable(any());
    }

    @Test
    void verifyRepliesWhenNoLogChannelConfigured() {
        enableVerification();
        when(logRoutingService.channelIdFor("guild-1", LogEventType.VERIFY_REQUESTED)).thenReturn(Optional.empty());
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(contains("log kanál"));
        verify(verifiedRoleResolver, never()).resolveAssignable(any());
    }

    @Test
    void verifyRepliesWhenVerifiedRoleIsUnavailable() {
        enableVerification();
        configureLogChannel();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenThrow(VerifiedRoleUnavailableException.notConfigured());
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(contains("nie je dostupná"));
        verify(verifyRateLimiter, never()).checkAndRecordAttempt(any(), any());
    }

    @Test
    void verifyDoesNotConsumeRateLimitWhenAlreadyVerifiedLocally() {
        enableVerification();
        configureLogChannel();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        stubStringOption("ais_id", "12345");
        Mockito.doThrow(AlreadyVerifiedException.discordUserAlreadyVerified("discord-1"))
                .when(verificationService).assertValidAndNotAlreadyVerified("discord-1", "guild-1", "12345");
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(AlreadyVerifiedException.discordUserAlreadyVerified("discord-1").getMessage());
        verify(verifyRateLimiter, never()).checkAndRecordAttempt(any(), any());
        verify(verificationService, never()).checkEligibility(any(), any(), any());
    }

    @Test
    void verifyRepliesWhenRateLimited() {
        enableVerification();
        configureLogChannel();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        stubStringOption("ais_id", "12345");
        when(verifyRateLimiter.checkAndRecordAttempt("discord-1", "guild-1")).thenReturn(Optional.of(5L));
        stubTextReply();
        stubEditMessage();

        listener.dispatch(event, null);

        // Throttle + LDAP path now runs on verifyExecutor, off the calling thread - wait for it.
        // The rate-limit outcome edits the "⏳ Overujem..." progress message in place rather than
        // sending a new followup - see handleVerify's replaceProgressMessage.
        verify(hook, timeout(1000)).editMessageById(eq(PROGRESS_MESSAGE_ID), contains("Vyčerpal"));
        verify(verificationService, never()).checkEligibility(any(), any(), any());
    }

    @Test
    void verifySkipsRateLimitInTestingMode() {
        VerificationProperties testingMode = new VerificationProperties(List.of("fei-stud"), "student:active", true);
        listener = new VerificationSlashCommandListener(
                verificationService, guildSettingsService, logRoutingService, verifiedRoleResolver,
                verifyRateLimiter, eventLogEmbedSender, testingMode, pendingVerificationStore,
                adminSettingsService);
        enableVerification();
        configureLogChannel();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        stubStringOption("ais_id", "12345");
        when(verificationService.checkEligibility("discord-1", "guild-1", "12345")).thenReturn("student@stuba.sk");
        when(pendingVerificationStore.create(any(), any(), any())).thenReturn("token-1");
        stubTextReply(); // the "⏳ Overujem..." in-progress message, edited in place below
        stubEditMessage();

        listener.dispatch(event, null);

        // Wait for verifyExecutor to reach the point where it would (not) call the rate limiter.
        verify(pendingVerificationStore, timeout(1000)).create(any(), any(), any());
        verify(verifyRateLimiter, never()).checkAndRecordAttempt(any(), any());
    }

    @Test
    void verifySendsConfirmationEmbedWithConfirmAndCancelButtonsOnEligibleAisId() {
        enableVerification();
        configureLogChannel();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        when(verifyRateLimiter.checkAndRecordAttempt("discord-1", "guild-1")).thenReturn(Optional.empty());
        stubStringOption("ais_id", "12345");
        when(verificationService.checkEligibility("discord-1", "guild-1", "12345")).thenReturn("student@stuba.sk");
        when(pendingVerificationStore.create("discord-1", "guild-1", "12345")).thenReturn("token-1");
        stubTextReply(); // the "⏳ Overujem..." in-progress message, edited into the embed below
        WebhookMessageEditAction<Message> action = stubEditMessage();

        listener.dispatch(event, null);

        // Success edits the progress message in place (editMessageById) rather than sending a
        // separate followup - see handleVerify.
        verify(hook, timeout(1000)).editMessageById(eq(PROGRESS_MESSAGE_ID), eq(""));
        ArgumentCaptor<ItemComponent[]> captor = ArgumentCaptor.forClass(ItemComponent[].class);
        verify(action, timeout(1000)).setActionRow(captor.capture());
        List<Button> buttons = List.of((Button) captor.getValue()[0], (Button) captor.getValue()[1]);
        assertThat(buttons).extracting(Button::getId).containsExactly("verify_confirm:token-1", "verify_cancel:token-1");
    }

    @Test
    void verifyRepliesWithDomainExceptionMessageWhenStudentNotEligible() {
        enableVerification();
        configureLogChannel();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        when(verifyRateLimiter.checkAndRecordAttempt("discord-1", "guild-1")).thenReturn(Optional.empty());
        stubStringOption("ais_id", "12345");
        when(verificationService.checkEligibility("discord-1", "guild-1", "12345"))
                .thenThrow(AlreadyVerifiedException.discordUserAlreadyVerified("discord-1"));
        stubTextReply();
        stubEditMessage();

        listener.dispatch(event, null);

        verify(hook, timeout(1000)).editMessageById(eq(PROGRESS_MESSAGE_ID),
                eq(AlreadyVerifiedException.discordUserAlreadyVerified("discord-1").getMessage()));
        verify(pendingVerificationStore, never()).create(any(), any(), any());
    }

    @Test
    void verifyRepliesWithGenericErrorOnUnexpectedException() {
        enableVerification();
        configureLogChannel();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        when(verifyRateLimiter.checkAndRecordAttempt("discord-1", "guild-1")).thenReturn(Optional.empty());
        stubStringOption("ais_id", "12345");
        when(verificationService.checkEligibility(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        stubTextReply();
        stubEditMessage();

        listener.dispatch(event, null);

        verify(hook, timeout(1000)).editMessageById(eq(PROGRESS_MESSAGE_ID), eq("Nastala neočakávaná chyba, skús to prosím neskôr."));
    }

    // ---- /code ----

    @Test
    void codeRepliesWithDisabledMessageWhenVerificationIsDisabled() {
        asCommand("code");
        disableVerification();
        stubStringOption("code", "ABC123");
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(contains("vypnutá"));
    }

    @Test
    void codeRepliesWhenNotAServerMember() {
        asCommand("code");
        enableVerification();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        when(event.getMember()).thenReturn(null);
        stubStringOption("code", "ABC123");
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(contains("nie si členom"));
    }

    @Test
    void codeAssignsRoleAndConfirmsOnSuccess() {
        asCommand("code");
        enableVerification();
        Role role = mock(Role.class);
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(role);
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of());
        when(event.getMember()).thenReturn(member);
        stubStringOption("code", "ABC123");
        VerifiedUser verifiedUser = new VerifiedUser("12345", "discord-1", "guild-1", "s@stuba.sk");
        when(verificationService.confirmVerification("discord-1", "guild-1", "ABC123")).thenReturn(verifiedUser);

        AuditableRestAction<Void> addRoleAction = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(guild.addRoleToMember(member, role)).thenReturn(addRoleAction);
        stubTextReply();

        listener.dispatch(event, null);

        verify(addRoleAction).complete();
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.CODE_CONFIRMED), any());
        verify(hook).sendMessage("Úspešne overené! Vitaj.");
        verify(verificationService, never()).revertVerification(any());
    }

    @Test
    void codeDoesNotReassignRoleWhenMemberAlreadyHasIt() {
        asCommand("code");
        enableVerification();
        Role role = mock(Role.class);
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(role);
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of(role));
        when(event.getMember()).thenReturn(member);
        stubStringOption("code", "ABC123");
        when(verificationService.confirmVerification("discord-1", "guild-1", "ABC123"))
                .thenReturn(new VerifiedUser("12345", "discord-1", "guild-1", "s@stuba.sk"));
        stubTextReply();

        listener.dispatch(event, null);

        verify(guild, never()).addRoleToMember(any(Member.class), any(Role.class));
        verify(hook).sendMessage("Úspešne overené! Vitaj.");
    }

    @Test
    void codeRollsBackVerifiedUserWhenRoleAssignmentFails() {
        asCommand("code");
        enableVerification();
        Role role = mock(Role.class);
        when(role.getId()).thenReturn("role-1");
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(role);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn("discord-1");
        when(member.getRoles()).thenReturn(List.of());
        when(event.getMember()).thenReturn(member);
        stubStringOption("code", "ABC123");
        VerifiedUser verifiedUser = new VerifiedUser("12345", "discord-1", "guild-1", "s@stuba.sk");
        when(verificationService.confirmVerification("discord-1", "guild-1", "ABC123")).thenReturn(verifiedUser);

        AuditableRestAction<Void> addRoleAction = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(addRoleAction.complete()).thenThrow(new RuntimeException("missing permission"));
        when(guild.addRoleToMember(member, role)).thenReturn(addRoleAction);
        stubTextReply();

        listener.dispatch(event, null);

        verify(verificationService).revertVerification(verifiedUser);
        verify(hook).sendMessage(contains("nemôžem ti priradiť rolu"));
        verify(eventLogEmbedSender, never()).send(any(), any(), any());
    }

    @Test
    void codeUsesConfiguredSuccessMessageWithPlaceholdersSubstituted() {
        asCommand("code");
        enableVerification();
        Role role = mock(Role.class);
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(role);
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of());
        when(event.getMember()).thenReturn(member);
        stubStringOption("code", "ABC123");
        VerifiedUser verifiedUser = new VerifiedUser("12345", "discord-1", "guild-1", "s@stuba.sk");
        when(verificationService.confirmVerification("discord-1", "guild-1", "ABC123")).thenReturn(verifiedUser);
        AuditableRestAction<Void> addRoleAction = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(guild.addRoleToMember(member, role)).thenReturn(addRoleAction);
        when(adminSettingsService.get(eq("cmd_settings_guild-1_code"), any(TypeReference.class), any()))
                .thenReturn(Map.of("message", "Vitaj {user} na {server}, AIS {ais_id}!"));
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Vitaj TestUser na TestGuild, AIS 12345!");
    }

    @Test
    void codeResolvesMultipleChannelTokensToDistinctClickableMentions() {
        asCommand("code");
        enableVerification();
        Role role = mock(Role.class);
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(role);
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of());
        when(event.getMember()).thenReturn(member);
        stubStringOption("code", "ABC123");
        VerifiedUser verifiedUser = new VerifiedUser("12345", "discord-1", "guild-1", "s@stuba.sk");
        when(verificationService.confirmVerification("discord-1", "guild-1", "ABC123")).thenReturn(verifiedUser);
        AuditableRestAction<Void> addRoleAction = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(guild.addRoleToMember(member, role)).thenReturn(addRoleAction);
        when(adminSettingsService.get(eq("cmd_settings_guild-1_code"), any(TypeReference.class), any()))
                .thenReturn(Map.of("message", "Pozri {channel=111111111111111111} a {channel=222222222222222222}."));
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Pozri <#111111111111111111> a <#222222222222222222>.");
    }

    @Test
    void codeRepliesWithDomainExceptionMessageOnInvalidCode() {
        asCommand("code");
        enableVerification();
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        when(event.getMember()).thenReturn(mock(Member.class));
        stubStringOption("code", "WRONG");
        when(verificationService.confirmVerification(any(), any(), any()))
                .thenThrow(InvalidVerificationCodeException.wrongCode());
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(InvalidVerificationCodeException.wrongCode().getMessage());
    }

    // ---- /find ----

    @Test
    void findRepliesWithNotFoundWhenNoVerifiedUserMatches() {
        asCommand("find");
        stubStringOption("ais_id", "99999");
        when(verificationService.findVerifiedUser("99999", "guild-1")).thenReturn(Optional.empty());
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage("No verified user found with AIS ID 99999.");
    }

    @Test
    void findRepliesWithFormattedDetailsWhenFound() {
        asCommand("find");
        stubStringOption("ais_id", "12345");
        VerifiedUser verifiedUser = new VerifiedUser("12345", "discord-1", "guild-1", "s@stuba.sk");
        when(verificationService.findVerifiedUser("12345", "guild-1")).thenReturn(Optional.of(verifiedUser));
        stubTextReply();

        listener.dispatch(event, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(captor.capture());
        // Email intentionally excluded from /find's reply (MEDIUM-001, security audit 2026-08-17) -
        // it's reachable by any guild member, unlike /manualverify/warn which are admin-only.
        assertThat(captor.getValue()).contains("12345").contains("discord-1").doesNotContain("s@stuba.sk");
    }

    // ---- /manualverify ----

    @Test
    void manualVerifyRepliesWhenTargetUserNotFound() {
        asCommand("manualverify");
        stubMemberOption("user", null);
        stubStringOption("ais_id", "12345");
        stubStringOption("email", "s@stuba.sk");
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage("User not found in this server.");
        verify(verifiedRoleResolver, never()).resolveAssignable(any());
    }

    @Test
    void manualVerifySucceedsAndAssignsRole() {
        asCommand("manualverify");
        Member target = mock(Member.class);
        when(target.getId()).thenReturn("target-1");
        when(target.getRoles()).thenReturn(List.of());
        User targetUser = mock(User.class);
        when(targetUser.getName()).thenReturn("Target");
        when(target.getUser()).thenReturn(targetUser);
        stubMemberOption("user", target);
        stubStringOption("ais_id", "12345");
        stubStringOption("email", "s@stuba.sk");
        Role role = mock(Role.class);
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(role);
        VerifiedUser verifiedUser = new VerifiedUser("12345", "target-1", "guild-1", "s@stuba.sk");
        when(verificationService.manuallyVerify("target-1", "guild-1", "12345", "s@stuba.sk")).thenReturn(verifiedUser);
        AuditableRestAction<Void> addRoleAction = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(guild.addRoleToMember(target, role)).thenReturn(addRoleAction);
        stubTextReply();

        listener.dispatch(event, null);

        verify(addRoleAction).complete();
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.MANUAL_VERIFY_PERFORMED), any());
        verify(hook).sendMessage(contains("has been manually verified"));
    }

    @Test
    void manualVerifyRollsBackWhenRoleAssignmentFails() {
        asCommand("manualverify");
        Member target = mock(Member.class);
        when(target.getId()).thenReturn("target-1");
        when(target.getRoles()).thenReturn(List.of());
        stubMemberOption("user", target);
        stubStringOption("ais_id", "12345");
        stubStringOption("email", "s@stuba.sk");
        Role role = mock(Role.class);
        when(role.getName()).thenReturn("Verified");
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(role);
        VerifiedUser verifiedUser = new VerifiedUser("12345", "target-1", "guild-1", "s@stuba.sk");
        when(verificationService.manuallyVerify("target-1", "guild-1", "12345", "s@stuba.sk")).thenReturn(verifiedUser);
        AuditableRestAction<Void> addRoleAction = mock(AuditableRestAction.class, Mockito.RETURNS_SELF);
        when(addRoleAction.complete()).thenThrow(new RuntimeException("missing permission"));
        when(guild.addRoleToMember(target, role)).thenReturn(addRoleAction);
        stubTextReply();

        listener.dispatch(event, null);

        verify(verificationService).revertVerification(verifiedUser);
        verify(hook).sendMessage(contains("Databázový záznam bol vrátený späť"));
    }

    @Test
    void manualVerifyRepliesWithDomainExceptionWhenAlreadyVerified() {
        asCommand("manualverify");
        Member target = mock(Member.class);
        stubMemberOption("user", target);
        stubStringOption("ais_id", "12345");
        stubStringOption("email", "s@stuba.sk");
        when(verifiedRoleResolver.resolveAssignable(guild)).thenReturn(mock(Role.class));
        when(verificationService.manuallyVerify(any(), any(), any(), any()))
                .thenThrow(AlreadyVerifiedException.aisIdAlreadyVerified("12345"));
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).sendMessage(AlreadyVerifiedException.aisIdAlreadyVerified("12345").getMessage());
    }

    // ---- dispatch routing ----

    @Test
    void dispatchIgnoresUnknownCommandNames() {
        asCommand("someothercommand");

        listener.dispatch(event, null);

        verify(event, never()).deferReply(anyBoolean());
    }

    // ---- ephemeral ----
    // deferReply(ephemeral) only covers the initial "thinking..." placeholder - every later
    // hook.sendMessage(...)/editMessageById(...) followup is a separate message that does NOT
    // inherit it on its own, so each handle* method must also call hook.setEphemeral(ephemeral).

    @Test
    void verifySetsHookEphemeralToConfiguredOverride() {
        disableVerification();
        stubTextReply();

        listener.dispatch(event, false);

        verify(hook).setEphemeral(false);
    }

    @Test
    void verifyDefaultsHookEphemeralToTrueWhenOverrideIsNull() {
        disableVerification();
        stubTextReply();

        listener.dispatch(event, null);

        verify(hook).setEphemeral(true);
    }

    @Test
    void codeSetsHookEphemeralToConfiguredOverride() {
        asCommand("code");
        disableVerification();
        stubStringOption("code", "ABC123");
        stubTextReply();

        listener.dispatch(event, false);

        verify(hook).setEphemeral(false);
    }

    @Test
    void findSetsHookEphemeralToConfiguredOverride() {
        asCommand("find");
        stubStringOption("ais_id", "99999");
        when(verificationService.findVerifiedUser("99999", "guild-1")).thenReturn(Optional.empty());
        stubTextReply();

        listener.dispatch(event, false);

        verify(hook).setEphemeral(false);
    }

    @Test
    void manualVerifySetsHookEphemeralToConfiguredOverride() {
        asCommand("manualverify");
        stubMemberOption("user", null);
        stubStringOption("ais_id", "12345");
        stubStringOption("email", "s@stuba.sk");
        stubTextReply();

        listener.dispatch(event, false);

        verify(hook).setEphemeral(false);
    }

    // ---- verifyProgressMessage ----

    @Test
    void verifyProgressMessageOmitsQueueNoteWhenPositionFitsInThePool() {
        assertThat(listener.verifyProgressMessage(1)).doesNotContain("fronte");
        assertThat(listener.verifyProgressMessage(10)).doesNotContain("fronte");
    }

    @Test
    void verifyProgressMessageSaysFirstInQueueWhenOneOverPoolSize() {
        assertThat(listener.verifyProgressMessage(11)).contains("si na rade ako prvý");
    }

    @Test
    void verifyProgressMessageCountsOthersAheadWhenFurtherBackInQueue() {
        assertThat(listener.verifyProgressMessage(13)).contains("2 pred tebou");
    }
}
