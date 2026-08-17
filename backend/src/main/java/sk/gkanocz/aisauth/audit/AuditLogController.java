package sk.gkanocz.aisauth.audit;

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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final GuildAccessService guildAccessService;
    private final ObjectMapper objectMapper;

    @ManagerAccess
    @GetMapping("/audit-logs")
    public List<AuditLogResponse> getAuditLogs(
            @AuthenticationPrincipal Claims claims,
            @RequestParam String category,
            @RequestParam String guildId,
            @RequestParam(defaultValue = "200") int limit) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return auditLogRepository
                .findByCategoryAndGuildIdOrderByCreatedAtDesc(category, guildId, PageRequest.of(0, limit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        Map<String, Object> details = log.getDetails() == null
                ? null
                : objectMapper.readValue(log.getDetails(), new TypeReference<Map<String, Object>>() { });
        return new AuditLogResponse(
                log.getId(), log.getCategory(), log.getAction(), log.getGuildId(), log.getGuildName(),
                log.getChannelId(), log.getChannelName(), log.getUserId(), log.getUsername(),
                details, log.getCreatedAt());
    }

    public record AuditLogResponse(
            Long id,
            String category,
            String action,
            @JsonProperty("guild_id") String guildId,
            @JsonProperty("guild_name") String guildName,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("channel_name") String channelName,
            @JsonProperty("user_id") String userId,
            String username,
            Map<String, Object> details,
            @JsonProperty("created_at") LocalDateTime createdAt) {
    }
}
