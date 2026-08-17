package sk.gkanocz.aisauth.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.ManagerAccess;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogRepository accessLogRepository;
    private final GuildAccessService guildAccessService;

    @ManagerAccess
    @GetMapping("/access-logs")
    public List<AccessLogResponse> getAccessLogs(
            @AuthenticationPrincipal Claims claims,
            @RequestParam String guildId,
            @RequestParam(defaultValue = "100") int limit) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return accessLogRepository.findByGuildIdOrderByCreatedAtDesc(guildId, PageRequest.of(0, limit)).stream()
                .map(AccessLogResponse::from)
                .toList();
    }

    public record AccessLogResponse(
            Long id,
            @JsonProperty("discord_id") String discordId,
            String username,
            String action,
            @JsonProperty("guild_id") String guildId,
            String ip,
            @JsonProperty("created_at") LocalDateTime createdAt) {

        static AccessLogResponse from(AccessLog log) {
            return new AccessLogResponse(
                    log.getId(), log.getDiscordId(), log.getUsername(), log.getAction(),
                    log.getGuildId(), log.getIp(), log.getCreatedAt());
        }
    }
}
