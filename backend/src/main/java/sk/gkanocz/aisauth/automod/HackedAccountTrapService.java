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

@Service
@RequiredArgsConstructor
public class HackedAccountTrapService {

    private final AdminSettingsService adminSettingsService;
    private final GuildSettingsService guildSettingsService;
    private final ObjectMapper objectMapper;

    public HackedAccountTrapSettings get(String guildId) {
        GuildSettings guildSettings = guildSettingsService.getOrCreate(guildId);
        HackedAccountTrapSettings defaults = HackedAccountTrapSettings.defaults(guildSettings.getSpamTrapChannelId());

        Map<String, Object> merged = new LinkedHashMap<>(
                objectMapper.convertValue(defaults, new TypeReference<Map<String, Object>>() { }));
        merged.putAll(adminSettingsService.get(key(guildId), new TypeReference<Map<String, Object>>() { }, Map.of()));
        return objectMapper.convertValue(merged, HackedAccountTrapSettings.class);
    }

    @Transactional
    public HackedAccountTrapSettings save(Guild guild, HackedAccountTrapSaveRequest request) {
        validate(guild, request);

        HackedAccountTrapSettings settings = new HackedAccountTrapSettings(
                request.enabled(), request.trapChannelId(),
                Boolean.TRUE.equals(request.deleteTriggerMessage()), Boolean.TRUE.equals(request.ignoreAdministrators()),
                distinct(request.exemptRoleIds()),
                Boolean.TRUE.equals(request.deleteMessageHistory()),
                request.deleteMessageHistorySeconds() == null ? 0 : request.deleteMessageHistorySeconds(),
                Boolean.TRUE.equals(request.dmUser()), request.dmMessage().trim(),
                blankToDefault(request.reason().trim(), "Hacked account trap triggered"));

        adminSettingsService.set(key(guild.getId()), settings);
        guildSettingsService.updateField(guild.getId(), "spam_trap_channel_id", settings.trapChannelId());
        return settings;
    }

    private void validate(Guild guild, HackedAccountTrapSaveRequest request) {
        if (request.enabled() == null) {
            throw InvalidRequestException.withMessage("Invalid module state");
        }
        if (request.exemptRoleIds() == null) {
            throw InvalidRequestException.withMessage("Invalid exempt roles");
        }
        if (request.dmMessage() == null || request.dmMessage().length() > 2000
                || request.reason() == null || request.reason().length() > 512) {
            throw InvalidRequestException.withMessage("DM message or reason is too long");
        }
        if (Boolean.TRUE.equals(request.deleteMessageHistory())
                && (request.deleteMessageHistorySeconds() == null
                    || !HackedAccountTrapSettings.DELETE_MESSAGE_HISTORY_SECONDS_OPTIONS.contains(request.deleteMessageHistorySeconds()))) {
            throw InvalidRequestException.withMessage("Invalid delete message history duration");
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
    }

    private boolean allRolesValid(Guild guild, List<String> roleIds) {
        return roleIds.stream().allMatch(id -> !id.equals(guild.getId()) && guild.getRoleById(id) != null);
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
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
            Boolean deleteTriggerMessage,
            Boolean ignoreAdministrators,
            List<String> exemptRoleIds,
            Boolean deleteMessageHistory,
            Integer deleteMessageHistorySeconds,
            Boolean dmUser,
            String dmMessage,
            String reason) {
    }
}
