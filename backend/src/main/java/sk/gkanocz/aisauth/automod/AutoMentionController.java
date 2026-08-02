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
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auto-mentions")
@RequiredArgsConstructor
public class AutoMentionController {

    private final AutoMentionRepository autoMentionRepository;
    private final GuildAccessService guildAccessService;
    private final AdminSettingsService adminSettingsService;

    @GetMapping("/enabled")
    public Map<String, Boolean> getEnabled(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return Map.of("enabled", adminSettingsService.get("automentions_enabled_" + guildId, Boolean.class, false));
    }

    @PostMapping("/enabled")
    public Map<String, Boolean> setEnabled(@AuthenticationPrincipal Claims claims, @RequestBody SetEnabledRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        adminSettingsService.set("automentions_enabled_" + request.guildId(), enabled);
        return Map.of("success", true, "enabled", enabled);
    }

    @GetMapping
    public List<AutoMentionResponse> getAutoMentions(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return autoMentionRepository.findByGuildIdOrderByCreatedAtAsc(guildId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @Transactional
    public AutoMentionResponse createAutoMention(@AuthenticationPrincipal Claims claims, @RequestBody AutoMentionRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        if (autoMentionRepository.findByGuildIdAndChannelId(request.guildId(), request.channelId()).isPresent()) {
            throw AutoMentionExistsException.forChannel();
        }
        AutoMention mention = new AutoMention(request.guildId(), request.channelId(), request.roleId());
        if (Boolean.FALSE.equals(request.enabled())) {
            mention.update(request.channelId(), request.roleId(), false);
        }
        return toResponse(autoMentionRepository.save(mention));
    }

    @PatchMapping("/{id}")
    @Transactional
    public AutoMentionResponse updateAutoMention(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestBody AutoMentionRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        AutoMention mention = autoMentionRepository.findByIdAndGuildId(id, request.guildId())
                .orElseThrow(AutoMentionNotFoundException::create);
        mention.update(request.channelId(), request.roleId(), request.enabled() == null || request.enabled());
        return toResponse(mention);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Boolean> deleteAutoMention(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        autoMentionRepository.deleteByIdAndGuildId(id, guildId);
        return Map.of("success", true);
    }

    private AutoMentionResponse toResponse(AutoMention mention) {
        return new AutoMentionResponse(mention.getId(), mention.getChannelId(), mention.getRoleId(), mention.isEnabled());
    }

    public record AutoMentionRequest(
            String guildId,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("role_id") String roleId,
            Boolean enabled) {
    }

    public record SetEnabledRequest(String guildId, Boolean enabled) {
    }

    public record AutoMentionResponse(
            Long id,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("role_id") String roleId,
            boolean enabled) {
    }
}
