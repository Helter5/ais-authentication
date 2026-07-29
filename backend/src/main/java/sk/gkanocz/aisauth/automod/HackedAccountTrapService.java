package sk.gkanocz.aisauth.automod;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HackedAccountTrapService {

    private final AdminSettingsService adminSettingsService;
    private final GuildSettingsService guildSettingsService;
    private final ObjectMapper objectMapper;

    public HackedAccountTrapSettings get(String guildId) {
        GuildSettings guildSettings = guildSettingsService.getOrCreate(guildId);
        HackedAccountTrapSettings defaults = HackedAccountTrapSettings.defaults(
                guildSettings.getSpamTrapChannelId(), guildSettings.getSpamDeleteInterval());

        Map<String, Object> merged = new LinkedHashMap<>(
                objectMapper.convertValue(defaults, new TypeReference<Map<String, Object>>() { }));
        merged.putAll(adminSettingsService.get(key(guildId), new TypeReference<Map<String, Object>>() { }, Map.of()));
        return objectMapper.convertValue(merged, HackedAccountTrapSettings.class);
    }

    @Transactional
    public HackedAccountTrapSettings save(Guild guild, HackedAccountTrapSaveRequest request) {
        validate(guild, request);

        HackedAccountTrapSettings settings = new HackedAccountTrapSettings(
                request.enabled(), request.trapChannelId(), request.action(), request.timeoutMinutes(),
                Boolean.TRUE.equals(request.deleteTriggerMessage()), Boolean.TRUE.equals(request.deleteRecentMessages()),
                request.cleanupMinutes(), distinct(request.exemptRoleIds()), Boolean.TRUE.equals(request.ignoreAdministrators()),
                Boolean.TRUE.equals(request.dmUser()), request.dmMessage().trim(),
                blankToDefault(request.reason().trim(), "Hacked account trap triggered"),
                Boolean.TRUE.equals(request.incidentChannelEnabled()),
                blankToNull(request.incidentChannelCategoryId()), blankToNull(request.incidentChannelClosedCategoryId()),
                blankToDefault(request.incidentChannelNameTemplate().trim(), "hacked-{user}"),
                Boolean.TRUE.equals(request.incidentChannelIncludeUser()),
                request.incidentChannelMessage().trim(),
                Boolean.TRUE.equals(request.incidentChannelPostDmStatus()), Boolean.TRUE.equals(request.incidentChannelTagRoles()),
                distinct(request.incidentChannelTagRoleIds()));

        adminSettingsService.set(key(guild.getId()), settings);
        guildSettingsService.updateField(guild.getId(), "spam_trap_channel_id", settings.trapChannelId());
        guildSettingsService.updateField(guild.getId(), "spam_delete_interval", settings.cleanupMinutes());
        return settings;
    }

    private void validate(Guild guild, HackedAccountTrapSaveRequest request) {
        if (request.enabled() == null || request.action() == null
                || !Set.of("timeout", "kick", "ban").contains(request.action())) {
            throw InvalidRequestException.withMessage("Invalid module state or action");
        }
        if (request.timeoutMinutes() == null || request.timeoutMinutes() < 1 || request.timeoutMinutes() > 40320) {
            throw InvalidRequestException.withMessage("Timeout must be between 1 and 40320 minutes");
        }
        if (request.cleanupMinutes() == null || request.cleanupMinutes() < 1 || request.cleanupMinutes() > 1440) {
            throw InvalidRequestException.withMessage("Cleanup period must be between 1 and 1440 minutes");
        }
        if (request.exemptRoleIds() == null) {
            throw InvalidRequestException.withMessage("Invalid exempt roles");
        }
        if (request.dmMessage() == null || request.dmMessage().length() > 2000
                || request.reason() == null || request.reason().length() > 512) {
            throw InvalidRequestException.withMessage("DM message or reason is too long");
        }
        if (request.incidentChannelTagRoleIds() == null) {
            throw InvalidRequestException.withMessage("Invalid incident channel tag roles");
        }
        if (request.incidentChannelMessage() == null || request.incidentChannelMessage().length() > 2000) {
            throw InvalidRequestException.withMessage("Incident channel message is too long");
        }
        if (request.incidentChannelNameTemplate() == null
                || request.incidentChannelNameTemplate().isBlank()
                || request.incidentChannelNameTemplate().length() > 100) {
            throw InvalidRequestException.withMessage("Incident channel name must be between 1 and 100 characters");
        }

        TextChannel trapChannel = request.trapChannelId() == null ? null : guild.getTextChannelById(request.trapChannelId());
        if (request.trapChannelId() != null && trapChannel == null) {
            throw InvalidRequestException.withMessage("Invalid trap channel");
        }
        if (Boolean.TRUE.equals(request.enabled()) && request.trapChannelId() == null) {
            throw InvalidRequestException.withMessage("Choose a trap channel before enabling the module");
        }
        if (!allRolesValid(guild, request.exemptRoleIds())) {
            throw InvalidRequestException.withMessage("One or more exempt roles are invalid");
        }
        if (!allRolesValid(guild, request.incidentChannelTagRoleIds())) {
            throw InvalidRequestException.withMessage("One or more incident channel tag roles are invalid");
        }
        if (request.incidentChannelCategoryId() != null && guild.getCategoryById(request.incidentChannelCategoryId()) == null) {
            throw InvalidRequestException.withMessage("Invalid incident channel category");
        }
        if (request.incidentChannelClosedCategoryId() != null
                && guild.getCategoryById(request.incidentChannelClosedCategoryId()) == null) {
            throw InvalidRequestException.withMessage("Invalid closed incident channel category");
        }
    }

    private boolean allRolesValid(Guild guild, List<String> roleIds) {
        return roleIds.stream().allMatch(id -> !id.equals(guild.getId()) && guild.getRoleById(id) != null);
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String blankToDefault(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private String key(String guildId) {
        return "module_hacked_account_trap_" + guildId;
    }

    public record HackedAccountTrapSaveRequest(
            String guildId,
            Boolean enabled,
            String trapChannelId,
            String action,
            Integer timeoutMinutes,
            Boolean deleteTriggerMessage,
            Boolean deleteRecentMessages,
            Integer cleanupMinutes,
            List<String> exemptRoleIds,
            Boolean ignoreAdministrators,
            Boolean dmUser,
            String dmMessage,
            String reason,
            Boolean incidentChannelEnabled,
            String incidentChannelCategoryId,
            String incidentChannelClosedCategoryId,
            String incidentChannelNameTemplate,
            Boolean incidentChannelIncludeUser,
            String incidentChannelMessage,
            Boolean incidentChannelPostDmStatus,
            Boolean incidentChannelTagRoles,
            List<String> incidentChannelTagRoleIds) {
    }
}
