package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscordNamesTest {

    @Test
    void returnsNullWhenGuildIsNull() {
        assertThat(DiscordNames.memberName(null, "user-1")).isNull();
    }

    @Test
    void returnsNullWhenDiscordIdIsNull() {
        Guild guild = mock(Guild.class);

        assertThat(DiscordNames.memberName(guild, null)).isNull();
    }

    @Test
    void returnsNullWhenMemberIsNotCached() {
        Guild guild = mock(Guild.class);
        when(guild.getMemberById("user-1")).thenReturn(null);

        assertThat(DiscordNames.memberName(guild, "user-1")).isNull();
    }

    @Test
    void returnsTheCachedMembersUsername() {
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);
        when(user.getName()).thenReturn("Alice");
        when(member.getUser()).thenReturn(user);
        when(guild.getMemberById("user-1")).thenReturn(member);

        assertThat(DiscordNames.memberName(guild, "user-1")).isEqualTo("Alice");
    }
}
