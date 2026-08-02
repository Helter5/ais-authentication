package sk.gkanocz.aisauth.settings;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuildSettingsController {

    private final GuildSettingsService guildSettingsService;
    private final GuildAccessService guildAccessService;

    @GetMapping("/settings")
    public GuildSettingsResponse getSettings(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        GuildSettings settings = guildSettingsService.getOrCreate(guildId);

        return new GuildSettingsResponse(
                settings.getVerifiedRoleId(),
                settings.getInactiveRoleId(),
                settings.getSpamTrapChannelId(),
                settings.getSpamDeleteInterval(),
                settings.isVerificationEnabled(),
                settings.isTicketRetentionEnabled(),
                settings.getTicketRetentionDays());
    }

    @PatchMapping("/settings")
    public Map<String, Boolean> updateSetting(
            @AuthenticationPrincipal Claims claims, @RequestBody UpdateSettingRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        guildSettingsService.updateField(request.guildId(), request.field(), request.value());
        return Map.of("success", true);
    }

    @PatchMapping("/settings/bulk")
    public Map<String, Boolean> updateSettings(
            @AuthenticationPrincipal Claims claims, @RequestBody UpdateSettingsBulkRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        guildSettingsService.updateFields(request.guildId(), request.fields());
        return Map.of("success", true);
    }

    public record UpdateSettingRequest(String guildId, String field, Object value) {
    }

    public record UpdateSettingsBulkRequest(String guildId, Map<String, Object> fields) {
    }
}
