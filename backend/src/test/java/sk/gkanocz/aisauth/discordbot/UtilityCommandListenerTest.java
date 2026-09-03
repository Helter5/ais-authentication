package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
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
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;
import sk.gkanocz.aisauth.warn.Warn;
import sk.gkanocz.aisauth.warn.WarnService;
import sk.gkanocz.aisauth.warn.WarnThreshold;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilityCommandListenerTest {

    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private WarnService warnService;
    @Mock
    private VerifiedUserRepository verifiedUserRepository;
    @Mock
    private LogRoutingService logRoutingService;

    private final VerificationProperties verificationProperties =
            new VerificationProperties(List.of("FEI", "FIIT"), "ACTIVE", false);

    @Mock
    private SlashCommandInteractionEvent event;
    @Mock
    private Guild guild;
    @Mock
    private InteractionHook hook;
    @Mock
    private User actorUser;

    private UtilityCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new UtilityCommandListener(guildSettingsService, verificationProperties, warnService,
                verifiedUserRepository, logRoutingService);

        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(guild.getName()).thenReturn("My Guild");
        Mockito.lenient().when(guild.getTimeCreated()).thenReturn(OffsetDateTime.now());
        Mockito.lenient().when(event.getHook()).thenReturn(hook);
        Mockito.lenient().when(event.getUser()).thenReturn(actorUser);
        Mockito.lenient().when(actorUser.getName()).thenReturn("Requester");
        Mockito.lenient().when(actorUser.getEffectiveAvatarUrl()).thenReturn("http://avatar/requester");
        Mockito.lenient().when(event.deferReply(anyBoolean())).thenReturn(mock(ReplyCallbackAction.class));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<MessageEmbed> stubEditOriginalEmbeds() {
        WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> action =
                mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        when(hook.editOriginalEmbeds(captor.capture())).thenReturn(action);
        return captor;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<String> stubEditOriginal() {
        WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> action =
                mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(hook.editOriginal(captor.capture())).thenReturn(action);
        return captor;
    }

    private String fieldValue(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(f -> name.equals(f.getName()))
                .map(MessageEmbed.Field::getValue)
                .findFirst()
                .orElse(null);
    }

    // ---- dispatch routing ----

    @Test
    void dispatchIgnoresUnrelatedCommands() {
        when(event.getName()).thenReturn("warn");

        listener.dispatch(event, null);

        Mockito.verifyNoInteractions(guildSettingsService);
    }

    // ---- /info ----

    @Test
    void infoDefaultsToEphemeralWhenOverrideIsNull() {
        when(event.getName()).thenReturn("info");
        stubInfoHappyPath();
        stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        Mockito.verify(event).deferReply(true);
    }

    @Test
    void infoUsesExplicitEphemeralOverride() {
        when(event.getName()).thenReturn("info");
        stubInfoHappyPath();
        stubEditOriginalEmbeds();

        listener.dispatch(event, false);

        Mockito.verify(event).deferReply(false);
    }

    @SuppressWarnings("unchecked")
    private void stubRetrieveOwner(Member owner) {
        CacheRestAction<Member> action = mock(CacheRestAction.class);
        when(action.complete()).thenReturn(owner);
        when(guild.retrieveOwner()).thenReturn(action);
    }

    @SuppressWarnings("unchecked")
    private void stubRetrieveMemberById(String userId, Member member) {
        CacheRestAction<Member> action = mock(CacheRestAction.class);
        when(action.complete()).thenReturn(member);
        when(guild.retrieveMemberById(userId)).thenReturn(action);
    }

    private void stubInfoHappyPath() {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId("role-verified");
        settings.setInactiveRoleId("role-inactive");
        settings.setSpamTrapChannelId("channel-trap");
        settings.setSpamDeleteInterval(45);
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);

        WarnThreshold threshold = new WarnThreshold("guild-1", 3, "ban");
        when(warnService.getThresholds("guild-1")).thenReturn(List.of(threshold));

        Member owner = mock(Member.class);
        User ownerUser = mock(User.class);
        Mockito.lenient().when(ownerUser.getName()).thenReturn("OwnerName");
        Mockito.lenient().when(owner.getUser()).thenReturn(ownerUser);
        stubRetrieveOwner(owner);

        List<GuildChannel> channels = new ArrayList<>();
        channels.add(mock(GuildChannel.class));
        channels.add(mock(GuildChannel.class));
        Mockito.lenient().when(guild.getChannels()).thenReturn(channels);

        List<Role> roles = List.of(mock(Role.class));
        Mockito.lenient().when(guild.getRoles()).thenReturn(roles);
        Mockito.lenient().when(guild.getRoleById("role-verified")).thenReturn(mock(Role.class));
        Mockito.lenient().when(guild.getRoleById("role-inactive")).thenReturn(null);

        when(logRoutingService.channelIdFor("guild-1", LogEventType.WARN_ISSUED)).thenReturn(Optional.of("channel-warn"));
        when(logRoutingService.channelIdFor("guild-1", LogEventType.HACKED_ACCOUNT_TRAP_TRIGGERED)).thenReturn(Optional.empty());

        when(verifiedUserRepository.countByGuildId("guild-1")).thenReturn(7L);
    }

    @Test
    void infoBuildsEmbedFromCollaborators() {
        when(event.getName()).thenReturn("info");
        stubInfoHappyPath();
        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        MessageEmbed embed = captor.getValue();
        assertThat(embed.getTitle()).isEqualTo("My Guild - Server & Bot Info");
        assertThat(embed.getDescription()).isEqualTo("**Server ID:** guild-1");
        assertThat(fieldValue(embed, "Owner")).isEqualTo("OwnerName");
        assertThat(fieldValue(embed, "Roles (including @everyone)")).isEqualTo("1");
        assertThat(fieldValue(embed, "Total channels")).isEqualTo("2");
        assertThat(fieldValue(embed, "Allowed Faculties")).isEqualTo("FEI, FIIT");
        assertThat(fieldValue(embed, "Required Status")).isEqualTo("ACTIVE");
        assertThat(fieldValue(embed, "Warn Log Channel")).isEqualTo("<#channel-warn>");
        assertThat(fieldValue(embed, "Verified Users")).isEqualTo("7");
        assertThat(fieldValue(embed, "Verified Role")).isEqualTo("<@&role-verified>");
        assertThat(fieldValue(embed, "Inactive Role")).isEqualTo("Role ID: role-inactive (not found)");
        assertThat(fieldValue(embed, "Warn Limit Settings")).isEqualTo("**3 warns** → Ban");
        assertThat(fieldValue(embed, "Hacked Account Trap Channel")).isEqualTo("<#channel-trap>");
        assertThat(fieldValue(embed, "Spam Log Channel")).isEqualTo("Not set");
        assertThat(fieldValue(embed, "Spam Delete Interval")).isEqualTo("45 min");
    }

    @Test
    void infoShowsNotSetWhenNoWarnThresholdsConfigured() {
        when(event.getName()).thenReturn("info");
        stubInfoHappyPath();
        when(warnService.getThresholds("guild-1")).thenReturn(List.of());
        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        assertThat(fieldValue(captor.getValue(), "Warn Limit Settings")).isEqualTo("Not set");
    }

    @Test
    void infoRepliesWithErrorMessageOnFailure() {
        when(event.getName()).thenReturn("info");
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(new GuildSettings("guild-1"));
        when(warnService.getThresholds("guild-1")).thenReturn(List.of());
        when(guild.retrieveOwner()).thenThrow(new RuntimeException("discord unreachable"));
        ArgumentCaptor<String> captor = stubEditOriginal();

        listener.dispatch(event, null);

        assertThat(captor.getValue()).isEqualTo("An unexpected error occurred, try again later.");
    }

    // ---- /user ----

    private void stubUserOption(User target) {
        OptionMapping option = mock(OptionMapping.class);
        when(option.getAsUser()).thenReturn(target);
        when(event.getOption("user")).thenReturn(option);
    }

    private User targetUser(String id, String name, boolean bot) {
        User user = mock(User.class);
        Mockito.lenient().when(user.getId()).thenReturn(id);
        Mockito.lenient().when(user.getName()).thenReturn(name);
        Mockito.lenient().when(user.getEffectiveAvatarUrl()).thenReturn("http://avatar/" + id);
        Mockito.lenient().when(user.getTimeCreated()).thenReturn(OffsetDateTime.now());
        Mockito.lenient().when(user.isBot()).thenReturn(bot);
        return user;
    }

    @Test
    void userDefaultsToNonEphemeralWhenOverrideIsNull() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "Alice", false);
        stubUserOption(target);
        when(guild.retrieveMemberById("user-1")).thenThrow(new RuntimeException("not cached"));
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L);
        stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        Mockito.verify(event).deferReply(false);
    }

    @Test
    void userShowsNotInServerWhenMemberLookupFails() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "Alice", false);
        stubUserOption(target);
        when(guild.retrieveMemberById("user-1")).thenThrow(new RuntimeException("not cached"));
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L);
        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, true);

        MessageEmbed embed = captor.getValue();
        assertThat(fieldValue(embed, "Status")).isEqualTo("⚠️ Not in server");
        assertThat(fieldValue(embed, "❌ Not Verified")).isEqualTo("Not in the verified users database.");
        assertThat(fieldValue(embed, "Warnings")).isEqualTo("0");
    }

    private Member memberFor(User user) {
        Member member = mock(Member.class);
        Mockito.lenient().when(member.getUser()).thenReturn(user);
        Mockito.lenient().when(member.getColorRaw()).thenReturn(0x123456);
        Mockito.lenient().when(member.getEffectiveAvatarUrl()).thenReturn("http://avatar/member");
        Mockito.lenient().when(member.getTimeJoined()).thenReturn(OffsetDateTime.now());
        Mockito.lenient().when(member.getRoles()).thenReturn(List.of());
        return member;
    }

    @Test
    void userShowsFullMemberDetailsWithVerificationAndWarns() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "Alice", false);
        stubUserOption(target);

        Member member = memberFor(target);
        Mockito.lenient().when(member.getNickname()).thenReturn("Ally");
        Mockito.lenient().when(member.getTimeBoosted()).thenReturn(OffsetDateTime.now());
        Mockito.lenient().when(member.isPending()).thenReturn(true);
        Role role = mock(Role.class);
        Mockito.lenient().when(role.getId()).thenReturn("role-9");
        Mockito.lenient().when(member.getRoles()).thenReturn(List.of(role));
        stubRetrieveMemberById("user-1", member);

        VerifiedUser verifiedUser = new VerifiedUser("ais-1", "user-1", "guild-1", "alice@stuba.sk");
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.of(verifiedUser));

        Warn warn = new Warn("guild-1", "user-1", "mod-1", "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(1L);
        when(warnService.getWarns("user-1", "guild-1")).thenReturn(List.of(warn));

        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        MessageEmbed embed = captor.getValue();
        assertThat(fieldValue(embed, "Nickname")).isEqualTo("Ally");
        assertThat(fieldValue(embed, "Pending")).isEqualTo("⚠️ Has not accepted server rules yet");
        assertThat(fieldValue(embed, "Roles (1)")).isEqualTo("<@&role-9>");
        assertThat(fieldValue(embed, "✅ Verified")).contains("**AIS ID:** ais-1").contains("alice@stuba.sk");
        assertThat(fieldValue(embed, "⚠️ Warnings (1)")).contains("spam");
    }

    @Test
    void userShowsNoneRolesFieldWhenMemberHasNoRoles() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "Alice", false);
        stubUserOption(target);
        Member member = memberFor(target);
        stubRetrieveMemberById("user-1", member);
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L);
        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        assertThat(fieldValue(captor.getValue(), "Roles")).isEqualTo("*None*");
    }

    @Test
    void userTruncatesRoleListBeyondTwenty() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "Alice", false);
        stubUserOption(target);
        Member member = memberFor(target);
        List<Role> roles = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            Role role = mock(Role.class);
            Mockito.lenient().when(role.getId()).thenReturn("role-" + i);
            roles.add(role);
        }
        Mockito.lenient().when(member.getRoles()).thenReturn(roles);
        stubRetrieveMemberById("user-1", member);
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L);
        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        String roleField = fieldValue(captor.getValue(), "Roles (22)");
        assertThat(roleField).contains("*(+2 more)*");
        assertThat(roleField).doesNotContain("<@&role-21>");
    }

    @Test
    void userCapsWarnListAtThreeAndNotesRemainder() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "Alice", false);
        stubUserOption(target);
        Member member = memberFor(target);
        stubRetrieveMemberById("user-1", member);
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(5L);
        List<Warn> warns = List.of(
                new Warn("guild-1", "user-1", "mod-1", "reason1"),
                new Warn("guild-1", "user-1", "mod-1", "reason2"),
                new Warn("guild-1", "user-1", "mod-1", "reason3"));
        when(warnService.getWarns("user-1", "guild-1")).thenReturn(warns);
        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        String warnField = fieldValue(captor.getValue(), "⚠️ Warnings (5)");
        assertThat(warnField).contains("reason1").contains("reason2").contains("reason3");
        assertThat(warnField).contains("*…and 2 more*");
    }

    @Test
    void userMarksBotAccounts() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "BotUser", true);
        stubUserOption(target);
        Member member = memberFor(target);
        stubRetrieveMemberById("user-1", member);
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L);
        ArgumentCaptor<MessageEmbed> captor = stubEditOriginalEmbeds();

        listener.dispatch(event, null);

        assertThat(fieldValue(captor.getValue(), "Bot")).isEqualTo("🤖 This is a bot account");
    }

    @Test
    void userRepliesWithErrorMessageOnFailure() {
        when(event.getName()).thenReturn("user");
        User target = targetUser("user-1", "Alice", false);
        stubUserOption(target);
        when(guild.retrieveMemberById("user-1")).thenThrow(new RuntimeException("not cached"));
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1"))
                .thenThrow(new RuntimeException("db down"));
        ArgumentCaptor<String> captor = stubEditOriginal();

        listener.dispatch(event, null);

        assertThat(captor.getValue()).isEqualTo("An unexpected error occurred, try again later.");
    }
}
