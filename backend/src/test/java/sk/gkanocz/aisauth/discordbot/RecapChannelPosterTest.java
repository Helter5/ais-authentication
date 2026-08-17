package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecapChannelPosterTest {

    private final RecapChannelPoster poster = new RecapChannelPoster();

    @Test
    void doesNothingWhenChannelIdIsNull() {
        Guild guild = mock(Guild.class);

        poster.post(guild, null, "content", List.of("line1"), "log.txt");

        verifyNoInteractions(guild);
    }

    @Test
    void doesNothingWhenChannelIsNotFound() {
        Guild guild = mock(Guild.class);
        when(guild.getTextChannelById("channel-1")).thenReturn(null);

        poster.post(guild, "channel-1", "content", List.of("line1"), "log.txt");

        verify(guild).getTextChannelById("channel-1");
        Mockito.verifyNoMoreInteractions(guild);
    }

    @Test
    void postsMessageWithLogFileAttachmentWhenChannelExists() {
        Guild guild = mock(Guild.class);
        TextChannel channel = mock(TextChannel.class);
        when(guild.getTextChannelById("channel-1")).thenReturn(channel);
        MessageCreateAction action = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        when(channel.sendMessage("Recap")).thenReturn(action);

        poster.post(guild, "channel-1", "Recap", List.of("line1", "line2"), "log.txt");

        verify(channel).sendMessage("Recap");
        verify(action).addFiles(any(net.dv8tion.jda.api.utils.FileUpload.class));
        verify(action).queue();
    }

    @Test
    void swallowsExceptionsWhilePosting() {
        Guild guild = mock(Guild.class);
        when(guild.getTextChannelById("channel-1")).thenThrow(new RuntimeException("discord unreachable"));

        poster.post(guild, "channel-1", "Recap", List.of("line1"), "log.txt");

        verify(guild).getTextChannelById("channel-1");
    }
}
