package sk.gkanocz.aisauth.auth;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibleGuildsResolverTest {

    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private JDA jda;

    private EligibleGuildsResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EligibleGuildsResolver(discordBotService, adminSettingsService);
    }

    private Guild mockGuild(String id) {
        Guild guild = mock(Guild.class);
        lenient().when(guild.getId()).thenReturn(id);
        return guild;
    }

    @Test
    void returnsEmptyListWhenBotIsNotConnected() {
        when(discordBotService.jda()).thenReturn(Optional.empty());

        assertThat(resolver.computeEligibleGuildIds("discord-1", false)).isEmpty();
    }

    @Test
    void superAdminGetsEveryGuildRegardlessOfAllowlist() {
        Guild guild = mockGuild("guild-1");
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuilds()).thenReturn(List.of(guild));

        List<String> result = resolver.computeEligibleGuildIds("discord-1", true);

        assertThat(result).containsExactly("guild-1");
    }

    @Test
    void regularUserExcludedWhenGuildNotOnAllowlist() {
        Guild guild = mockGuild("guild-1");
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(adminSettingsService.get(eq("allowed_guild_ids"), any(TypeReference.class), any()))
                .thenReturn(List.of("some-other-guild"));

        assertThat(resolver.computeEligibleGuildIds("discord-1", false)).isEmpty();
    }

    @Test
    void regularUserExcludedWhenNoManagerRolesConfigured() {
        Guild guild = mockGuild("guild-1");
        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(adminSettingsService.get(eq("allowed_guild_ids"), any(TypeReference.class), any()))
                .thenReturn(List.of("guild-1"));
        when(adminSettingsService.dashboardSettings("guild-1")).thenReturn(new DashboardSettings(List.of()));

        assertThat(resolver.computeEligibleGuildIds("discord-1", false)).isEmpty();
    }

    @Test
    void regularUserEligibleWhenTheyHoldAConfiguredManagerRole() {
        Guild guild = mockGuild("guild-1");
        Role managerRole = mock(Role.class);
        when(managerRole.getId()).thenReturn("role-manager");
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of(managerRole));
        CacheRestAction<Member> action = mock(CacheRestAction.class);
        when(action.complete()).thenReturn(member);
        when(guild.retrieveMemberById("discord-1")).thenReturn(action);

        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(adminSettingsService.get(eq("allowed_guild_ids"), any(TypeReference.class), any()))
                .thenReturn(List.of("guild-1"));
        when(adminSettingsService.dashboardSettings("guild-1")).thenReturn(new DashboardSettings(List.of("role-manager")));

        assertThat(resolver.computeEligibleGuildIds("discord-1", false)).containsExactly("guild-1");
    }

    @Test
    void regularUserExcludedWhenTheyDontHoldTheManagerRole() {
        Guild guild = mockGuild("guild-1");
        Role otherRole = mock(Role.class);
        when(otherRole.getId()).thenReturn("role-other");
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of(otherRole));
        CacheRestAction<Member> action = mock(CacheRestAction.class);
        when(action.complete()).thenReturn(member);
        when(guild.retrieveMemberById("discord-1")).thenReturn(action);

        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(adminSettingsService.get(eq("allowed_guild_ids"), any(TypeReference.class), any()))
                .thenReturn(List.of("guild-1"));
        when(adminSettingsService.dashboardSettings("guild-1")).thenReturn(new DashboardSettings(List.of("role-manager")));

        assertThat(resolver.computeEligibleGuildIds("discord-1", false)).isEmpty();
    }

    @Test
    void memberFetchFailureIsTreatedAsIneligibleNotAsACrash() {
        Guild guild = mockGuild("guild-1");
        when(guild.retrieveMemberById(anyString())).thenThrow(new RuntimeException("not cached"));

        when(discordBotService.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(adminSettingsService.get(eq("allowed_guild_ids"), any(TypeReference.class), any()))
                .thenReturn(List.of("guild-1"));
        when(adminSettingsService.dashboardSettings("guild-1")).thenReturn(new DashboardSettings(List.of("role-manager")));

        assertThat(resolver.computeEligibleGuildIds("discord-1", false)).isEmpty();
    }
}
