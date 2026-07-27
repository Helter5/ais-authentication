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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/autodelete")
@RequiredArgsConstructor
public class AutoDeleteController {

    private final AutoDeleteConfigRepository autoDeleteConfigRepository;
    private final GuildAccessService guildAccessService;
    private final AdminSettingsService adminSettingsService;
    private final ObjectMapper objectMapper;

    @GetMapping("/enabled")
    public Map<String, Boolean> getEnabled(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return Map.of("enabled", adminSettingsService.get("autodelete_enabled_" + guildId, Boolean.class, false));
    }

    @PostMapping("/enabled")
    public Map<String, Boolean> setEnabled(@AuthenticationPrincipal Claims claims, @RequestBody SetEnabledRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        adminSettingsService.set("autodelete_enabled_" + request.guildId(), enabled);
        return Map.of("success", true, "enabled", enabled);
    }

    @GetMapping
    public List<AutoDeleteConfigResponse> getConfigs(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return autoDeleteConfigRepository.findByGuildIdOrderByCreatedAtAsc(guildId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @Transactional
    public AutoDeleteConfigResponse createConfig(@AuthenticationPrincipal Claims claims, @RequestBody AutoDeleteConfigRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        if (!autoDeleteConfigRepository.findByGuildIdAndChannelId(request.guildId(), request.channelId()).isEmpty()) {
            throw AutoDeleteConfigExistsException.forChannel();
        }
        AutoDeleteConfig config = new AutoDeleteConfig(
                request.guildId(), request.channelId(),
                request.delaySeconds() == null ? 60 : request.delaySeconds(),
                writeJson(request.ignoreRoleIds()), writeJson(request.ignoreUserIds()),
                request.ignoreBots() == null || request.ignoreBots(),
                request.ignorePinned() == null || request.ignorePinned(),
                Boolean.TRUE.equals(request.notifyUser()),
                request.notifyVia() == null ? "channel" : request.notifyVia(),
                request.notifyMessage() == null
                        ? "Your message in {channel} was automatically deleted." : request.notifyMessage(),
                request.notifyDeleteBotMsg() == null || request.notifyDeleteBotMsg(),
                Math.max(3, request.notifyDeleteDelay() == null ? 5 : request.notifyDeleteDelay()));
        return toResponse(autoDeleteConfigRepository.save(config));
    }

    @PatchMapping("/{id}")
    @Transactional
    public AutoDeleteConfigResponse updateConfig(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestBody AutoDeleteConfigRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        AutoDeleteConfig config = autoDeleteConfigRepository.findByIdAndGuildId(id, request.guildId())
                .orElseThrow(AutoDeleteConfigNotFoundException::create);
        config.update(
                request.channelId(),
                request.delaySeconds() == null ? 60 : request.delaySeconds(),
                writeJson(request.ignoreRoleIds()), writeJson(request.ignoreUserIds()),
                request.ignoreBots() == null || request.ignoreBots(),
                request.ignorePinned() == null || request.ignorePinned(),
                Boolean.TRUE.equals(request.notifyUser()),
                request.notifyVia() == null ? "channel" : request.notifyVia(),
                request.notifyMessage() == null
                        ? "Your message in {channel} was automatically deleted." : request.notifyMessage(),
                request.notifyDeleteBotMsg() == null || request.notifyDeleteBotMsg(),
                Math.max(3, request.notifyDeleteDelay() == null ? 5 : request.notifyDeleteDelay()));
        return toResponse(config);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Boolean> deleteConfig(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        autoDeleteConfigRepository.deleteByIdAndGuildId(id, guildId);
        return Map.of("success", true);
    }

    private String writeJson(List<String> values) {
        return objectMapper.writeValueAsString(values == null ? List.of() : values);
    }

    private List<String> readJson(String json) {
        return IgnoreListJson.read(objectMapper, json);
    }

    private AutoDeleteConfigResponse toResponse(AutoDeleteConfig config) {
        return new AutoDeleteConfigResponse(
                config.getId(), config.getGuildId(), config.getChannelId(), config.getDelaySeconds(),
                readJson(config.getIgnoreRoleIds()), readJson(config.getIgnoreUserIds()),
                config.isIgnoreBots(), config.isIgnorePinned(), config.isNotifyUser(), config.getNotifyVia(),
                config.getNotifyMessage(), config.isNotifyDeleteBotMsg(), config.getNotifyDeleteDelay());
    }

    public record SetEnabledRequest(String guildId, Boolean enabled) {
    }

    public record AutoDeleteConfigRequest(
            String guildId,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("delay_seconds") Integer delaySeconds,
            @JsonProperty("ignore_role_ids") List<String> ignoreRoleIds,
            @JsonProperty("ignore_user_ids") List<String> ignoreUserIds,
            @JsonProperty("ignore_bots") Boolean ignoreBots,
            @JsonProperty("ignore_pinned") Boolean ignorePinned,
            @JsonProperty("notify_user") Boolean notifyUser,
            @JsonProperty("notify_via") String notifyVia,
            @JsonProperty("notify_message") String notifyMessage,
            @JsonProperty("notify_delete_bot_msg") Boolean notifyDeleteBotMsg,
            @JsonProperty("notify_delete_delay") Integer notifyDeleteDelay) {
    }

    public record AutoDeleteConfigResponse(
            Long id,
            @JsonProperty("guild_id") String guildId,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("delay_seconds") int delaySeconds,
            @JsonProperty("ignore_role_ids") List<String> ignoreRoleIds,
            @JsonProperty("ignore_user_ids") List<String> ignoreUserIds,
            @JsonProperty("ignore_bots") boolean ignoreBots,
            @JsonProperty("ignore_pinned") boolean ignorePinned,
            @JsonProperty("notify_user") boolean notifyUser,
            @JsonProperty("notify_via") String notifyVia,
            @JsonProperty("notify_message") String notifyMessage,
            @JsonProperty("notify_delete_bot_msg") boolean notifyDeleteBotMsg,
            @JsonProperty("notify_delete_delay") int notifyDeleteDelay) {
    }
}
