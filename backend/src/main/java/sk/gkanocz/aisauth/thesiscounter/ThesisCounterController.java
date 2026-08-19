package sk.gkanocz.aisauth.thesiscounter;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
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
import sk.gkanocz.aisauth.auth.ManagerAccess;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/thesiscounter")
@RequiredArgsConstructor
public class ThesisCounterController {

    private final ThesisCounterConfigRepository thesisCounterConfigRepository;
    private final ThesisCounterService thesisCounterService;
    private final GuildAccessService guildAccessService;
    private final AdminSettingsService adminSettingsService;
    private final DiscordBotService discordBotService;

    @ManagerAccess
    @GetMapping("/enabled")
    public Map<String, Boolean> getEnabled(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return Map.of("enabled", adminSettingsService.get("thesiscounter_enabled_" + guildId, Boolean.class, false));
    }

    @ManagerAccess
    @PostMapping("/enabled")
    public Map<String, Boolean> setEnabled(@AuthenticationPrincipal Claims claims, @RequestBody SetEnabledRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        adminSettingsService.set("thesiscounter_enabled_" + request.guildId(), enabled);
        return Map.of("success", true, "enabled", enabled);
    }

    @ManagerAccess
    @GetMapping
    public List<ThesisCounterConfigResponse> getConfigs(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return thesisCounterConfigRepository.findByGuildIdOrderByCreatedAtAsc(guildId).stream()
                .map(this::toResponse)
                .toList();
    }

    @ManagerAccess
    @PostMapping
    @Transactional
    public ThesisCounterConfigResponse createConfig(@AuthenticationPrincipal Claims claims, @RequestBody ThesisCounterCreateRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        validate(request.channelId(), request.label(), request.targetDate());
        validateFormat(request.nameFormat());
        validateFormat(request.todayFormat());
        Guild guild = discordBotService.requireGuild(request.guildId());
        ThesisCounterConfig config = thesisCounterService.addCounter(
                guild, request.channelId(), request.label(), request.targetDate(), request.nameFormat(), request.todayFormat());
        return toResponse(config);
    }

    @ManagerAccess
    @PatchMapping("/{id}")
    @Transactional
    public ThesisCounterConfigResponse updateConfig(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestBody ThesisCounterUpdateRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        ThesisCounterConfig existing = thesisCounterConfigRepository.findByIdAndGuildId(id, request.guildId())
                .orElseThrow(ThesisCounterConfigNotFoundException::create);
        String newChannelId = request.channelId() != null && !request.channelId().isBlank()
                ? request.channelId() : existing.getChannelId();
        validate(newChannelId, request.label(), request.targetDate());
        validateFormat(request.nameFormat());
        validateFormat(request.todayFormat());
        Guild guild = discordBotService.requireGuild(request.guildId());
        ThesisCounterConfig config = thesisCounterService.editCounter(
                guild, existing.getChannelId(), newChannelId, request.label(), request.targetDate(), request.nameFormat(), request.todayFormat());
        return toResponse(config);
    }

    @ManagerAccess
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Boolean> deleteConfig(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        ThesisCounterConfig config = thesisCounterConfigRepository.findByIdAndGuildId(id, guildId)
                .orElseThrow(ThesisCounterConfigNotFoundException::create);
        Guild guild = discordBotService.requireGuild(guildId);
        thesisCounterService.removeCounter(guild, config);
        return Map.of("success", true);
    }

    private void validate(String channelId, String label, LocalDate targetDate) {
        if (channelId == null || channelId.isBlank()) {
            throw InvalidRequestException.withMessage("Choose a channel for the thesis counter.");
        }
        if (!"BP".equals(label) && !"DP".equals(label)) {
            throw InvalidRequestException.withMessage("label must be BP or DP.");
        }
        if (targetDate == null) {
            throw InvalidRequestException.withMessage("Give the thesis counter a target date.");
        }
    }

    private void validateFormat(String format) {
        if (format != null && format.length() > 100) {
            throw InvalidRequestException.withMessage("Channel name format can't exceed 100 characters.");
        }
    }

    private ThesisCounterConfigResponse toResponse(ThesisCounterConfig config) {
        long daysRemaining = thesisCounterService.daysRemaining(config);
        return new ThesisCounterConfigResponse(
                config.getId(), config.getGuildId(), config.getChannelId(), config.getLabel(),
                config.getTargetDate(), config.isActive(), daysRemaining,
                config.getNameFormat(), config.getTodayFormat());
    }

    public record SetEnabledRequest(String guildId, Boolean enabled) {
    }

    public record ThesisCounterCreateRequest(
            String guildId,
            @JsonProperty("channel_id") String channelId,
            String label,
            @JsonProperty("target_date") LocalDate targetDate,
            @JsonProperty("name_format") String nameFormat,
            @JsonProperty("today_format") String todayFormat) {
    }

    public record ThesisCounterUpdateRequest(
            String guildId,
            @JsonProperty("channel_id") String channelId,
            String label,
            @JsonProperty("target_date") LocalDate targetDate,
            @JsonProperty("name_format") String nameFormat,
            @JsonProperty("today_format") String todayFormat) {
    }

    public record ThesisCounterConfigResponse(
            Long id,
            @JsonProperty("guild_id") String guildId,
            @JsonProperty("channel_id") String channelId,
            String label,
            @JsonProperty("target_date") LocalDate targetDate,
            boolean active,
            @JsonProperty("days_remaining") long daysRemaining,
            @JsonProperty("name_format") String nameFormat,
            @JsonProperty("today_format") String todayFormat) {
    }
}
