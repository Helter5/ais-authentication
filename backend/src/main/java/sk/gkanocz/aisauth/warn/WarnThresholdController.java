package sk.gkanocz.aisauth.warn;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.ManagerAccess;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warn-thresholds")
@RequiredArgsConstructor
public class WarnThresholdController {

    private final WarnService warnService;
    private final GuildAccessService guildAccessService;

    @ManagerAccess
    @GetMapping
    public List<ThresholdResponse> getThresholds(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return warnService.getThresholds(guildId).stream()
                .map(ThresholdResponse::from)
                .toList();
    }

    @ManagerAccess
    @PostMapping
    public Map<String, Boolean> addThreshold(@AuthenticationPrincipal Claims claims, @RequestBody UpsertRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        warnService.upsertThreshold(request.guildId(), request.warnLimit(), request.action());
        return Map.of("success", true);
    }

    @ManagerAccess
    @DeleteMapping("/{limit}")
    public Map<String, Boolean> removeThreshold(
            @AuthenticationPrincipal Claims claims, @PathVariable Integer limit, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        warnService.removeThreshold(guildId, limit);
        return Map.of("success", true);
    }

    public record ThresholdResponse(@JsonProperty("warn_limit") Integer warnLimit, String action) {
        static ThresholdResponse from(WarnThreshold threshold) {
            return new ThresholdResponse(threshold.getWarnLimit(), threshold.getAction());
        }
    }

    public record UpsertRequest(
            String guildId, @JsonProperty("warn_limit") Integer warnLimit, String action) {
    }
}
