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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.gkanocz.aisauth.auth.GuildAccessService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuildSettingsController {

    private final GuildSettingsService guildSettingsService;
    private final GuildAccessService guildAccessService;
    private final VerificationStatusBroadcaster verificationStatusBroadcaster;

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
        if ("verification_enabled".equals(request.field())) {
            broadcastVerificationStatus(request.guildId());
        }
        return Map.of("success", true);
    }

    @PatchMapping("/settings/bulk")
    public Map<String, Boolean> updateSettings(
            @AuthenticationPrincipal Claims claims, @RequestBody UpdateSettingsBulkRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        guildSettingsService.updateFields(request.guildId(), request.fields());
        if (request.fields().containsKey("verification_enabled")) {
            broadcastVerificationStatus(request.guildId());
        }
        return Map.of("success", true);
    }

    /**
     * Not gated the way the maintenance stream is (that one's global and shown to every logged-in
     * manager) - verification_enabled is per-guild data, so this requires the same manage-guild
     * access as the plain GET /settings above.
     */
    @GetMapping("/settings/verification-stream")
    public SseEmitter streamVerificationStatus(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return verificationStatusBroadcaster.subscribe(guildId, guildSettingsService.getOrCreate(guildId).isVerificationEnabled());
    }

    private void broadcastVerificationStatus(String guildId) {
        verificationStatusBroadcaster.broadcast(guildId, guildSettingsService.getOrCreate(guildId).isVerificationEnabled());
    }

    public record UpdateSettingRequest(String guildId, String field, Object value) {
    }

    public record UpdateSettingsBulkRequest(String guildId, Map<String, Object> fields) {
    }
}
