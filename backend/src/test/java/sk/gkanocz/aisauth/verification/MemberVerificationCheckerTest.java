package sk.gkanocz.aisauth.verification;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberVerificationCheckerTest {

    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private VerifiedUserRepository verifiedUserRepository;

    @Mock
    private Guild guild;
    @Mock
    private Member member;

    private MemberVerificationChecker checker;

    @BeforeEach
    void setUp() {
        checker = new MemberVerificationChecker(guildSettingsService, verifiedUserRepository);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(member.getId()).thenReturn("member-1");
    }

    private GuildSettings settingsWithVerifiedRole(String roleId) {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId(roleId);
        return settings;
    }

    @Test
    void isVerifiedFalseWhenNoVerifiedRoleConfigured() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole(null));

        assertThat(checker.isVerified(guild, member)).isFalse();
    }

    @Test
    void isVerifiedFalseWhenMemberDoesNotHaveTheRole() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        when(member.getRoles()).thenReturn(List.of());

        assertThat(checker.isVerified(guild, member)).isFalse();
    }

    @Test
    void isVerifiedFalseWhenMemberHasRoleButNoVerifiedUsersRow() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        Role role = mock(Role.class);
        when(role.getId()).thenReturn("verified-role");
        when(member.getRoles()).thenReturn(List.of(role));
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("member-1", "guild-1")).thenReturn(false);

        assertThat(checker.isVerified(guild, member)).isFalse();
    }

    @Test
    void isVerifiedTrueWhenMemberHasRoleAndAVerifiedUsersRow() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        Role role = mock(Role.class);
        when(role.getId()).thenReturn("verified-role");
        when(member.getRoles()).thenReturn(List.of(role));
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("member-1", "guild-1")).thenReturn(true);

        assertThat(checker.isVerified(guild, member)).isTrue();
    }
}
