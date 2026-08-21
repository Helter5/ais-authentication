package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.ManagerAccess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/discord")
@RequiredArgsConstructor
public class DiscordResourcesController {

    private final DiscordBotService discordBotService;
    private final GuildAccessService guildAccessService;

    @ManagerAccess
    @GetMapping("/roles")
    public List<RoleResponse> getRoles(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);
        return guild.getRoles().stream()
                .filter(role -> !role.getId().equals(guild.getId()))
                .sorted(Comparator.comparingInt(Role::getPosition).reversed())
                .map(role -> new RoleResponse(role.getId(), role.getName(), hexColor(role), role.getPosition()))
                .toList();
    }

    @ManagerAccess
    @GetMapping("/channels")
    public List<ChannelResponse> getChannels(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);
        return guild.getTextChannels().stream()
                .sorted(Comparator.comparingInt(TextChannel::getPositionRaw))
                .map(channel -> new ChannelResponse(channel.getId(), channel.getName(), channel.getPositionRaw()))
                .toList();
    }

    @ManagerAccess
    @GetMapping("/categories")
    public List<ChannelResponse> getCategories(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);
        return guild.getCategories().stream()
                .sorted(Comparator.comparingInt(Category::getPositionRaw))
                .map(category -> new ChannelResponse(category.getId(), category.getName(), category.getPositionRaw()))
                .toList();
    }

    /**
     * Resolves a batch of member IDs to their current username - used to show "who exactly" (e.g.
     * a semester role-migration's or wipe's member list) as names instead of raw snowflakes. Cache
     * hits (already-known members) skip the network entirely; only actual cache misses go through
     * one batched gateway request instead of one REST call per ID. Any ID that still can't be
     * resolved (member left, request timed out) is just missing from the response - the frontend
     * falls back to showing the raw ID for those.
     */
    @ManagerAccess
    @GetMapping("/members")
    public List<MemberResponse> getMembers(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId, @RequestParam List<String> ids) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);

        List<String> distinctIds = ids.stream().distinct().toList();
        List<Member> resolved = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : distinctIds) {
            Member cached = guild.getMemberById(id);
            if (cached != null) {
                resolved.add(cached);
            } else {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            try {
                resolved.addAll(guild.retrieveMembersByIds(missing.toArray(new String[0]))
                        .setTimeout(10, TimeUnit.SECONDS).get());
            } catch (Exception e) {
                log.warn("Failed to resolve {} member(s) in guild {}: {}", missing.size(), guildId, e.getMessage());
            }
        }
        return resolved.stream().map(m -> new MemberResponse(m.getId(), m.getUser().getName())).toList();
    }

    @ManagerAccess
    @GetMapping("/emojis")
    public List<EmojiResponse> getEmojis(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);
        return guild.getEmojis().stream()
                .sorted(Comparator.comparing(RichCustomEmoji::getName))
                .map(emoji -> new EmojiResponse(
                        emoji.getId(), emoji.getName(), emoji.isAnimated(), emoji.getImageUrl(), emoji.getAsMention()))
                .toList();
    }

    private String hexColor(Role role) {
        return String.format("#%06X", role.getColorRaw() & 0xFFFFFF);
    }

    public record RoleResponse(String id, String name, String color, int position) {
    }

    public record ChannelResponse(String id, String name, int position) {
    }

    public record EmojiResponse(String id, String name, boolean animated, String url, String mention) {
    }

    public record MemberResponse(String id, String name) {
    }
}
