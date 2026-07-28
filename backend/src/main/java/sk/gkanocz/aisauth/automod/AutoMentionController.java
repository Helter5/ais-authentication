package sk.gkanocz.aisauth.automod;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auto-mentions")
@RequiredArgsConstructor
public class AutoMentionController {

    private final AutoMentionRepository autoMentionRepository;
    private final GuildAccessService guildAccessService;

    @GetMapping
    public List<AutoMentionResponse> getAutoMentions(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return autoMentionRepository.findByGuildIdOrderByCreatedAtAsc(guildId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @Transactional
    public Map<String, Boolean> addAutoMention(@AuthenticationPrincipal Claims claims, @RequestBody AutoMentionRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        if (autoMentionRepository.findByGuildIdAndChannelId(request.guildId(), request.channelId()).isPresent()) {
            throw AutoMentionExistsException.forChannel();
        }
        autoMentionRepository.save(new AutoMention(request.guildId(), request.channelId(), request.roleId()));
        return Map.of("success", true);
    }

    @PatchMapping("/{channelId}")
    @Transactional
    public Map<String, Object> toggleAutoMention(
            @AuthenticationPrincipal Claims claims, @PathVariable String channelId, @RequestBody GuildIdRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        AutoMention mention = autoMentionRepository.findByGuildIdAndChannelId(request.guildId(), channelId)
                .orElseThrow(AutoMentionNotFoundException::create);
        boolean enabled = mention.toggle();
        return Map.of("success", true, "enabled", enabled);
    }

    @DeleteMapping("/{channelId}")
    @Transactional
    public Map<String, Boolean> removeAutoMention(
            @AuthenticationPrincipal Claims claims, @PathVariable String channelId, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        autoMentionRepository.deleteByGuildIdAndChannelId(guildId, channelId);
        return Map.of("success", true);
    }

    private AutoMentionResponse toResponse(AutoMention mention) {
        return new AutoMentionResponse(mention.getChannelId(), mention.getRoleId(), mention.isEnabled());
    }

    public record AutoMentionRequest(
            String guildId,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("role_id") String roleId) {
    }

    public record GuildIdRequest(String guildId) {
    }

    public record AutoMentionResponse(
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("role_id") String roleId,
            boolean enabled) {
    }
}
