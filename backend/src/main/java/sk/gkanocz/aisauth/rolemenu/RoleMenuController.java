package sk.gkanocz.aisauth.rolemenu;

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

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/rolemenu")
@RequiredArgsConstructor
public class RoleMenuController {

    private static final Set<String> VALID_MESSAGE_TYPES = Set.of("NORMAL", "UNIQUE", "VERIFY", "DROP", "BINDING");

    private final RoleMenuConfigRepository roleMenuConfigRepository;
    private final RoleMenuService roleMenuService;
    private final GuildAccessService guildAccessService;
    private final AdminSettingsService adminSettingsService;
    private final DiscordBotService discordBotService;

    @ManagerAccess
    @GetMapping("/enabled")
    public Map<String, Boolean> getEnabled(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return Map.of("enabled", adminSettingsService.get("rolemenu_enabled_" + guildId, Boolean.class, false));
    }

    @ManagerAccess
    @PostMapping("/enabled")
    public Map<String, Boolean> setEnabled(@AuthenticationPrincipal Claims claims, @RequestBody SetEnabledRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        adminSettingsService.set("rolemenu_enabled_" + request.guildId(), enabled);
        return Map.of("success", true, "enabled", enabled);
    }

    @ManagerAccess
    @GetMapping
    public List<RoleMenuConfigResponse> getConfigs(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return roleMenuConfigRepository.findByGuildIdOrderByCreatedAtAsc(guildId).stream()
                .map(this::toResponse)
                .toList();
    }

    @ManagerAccess
    @PostMapping
    @Transactional
    public RoleMenuConfigResponse createConfig(@AuthenticationPrincipal Claims claims, @RequestBody RoleMenuConfigRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        validate(request);
        RoleMenuConfig config = new RoleMenuConfig(
                request.guildId(), request.channelId(),
                request.configName(), request.title(), request.description(),
                request.uiType(), request.messageType(),
                Boolean.TRUE.equals(request.requireVerified()),
                roleMenuService.writeOptions(request.options()),
                roleMenuService.writeRoleIds(request.allowedRoleIds()),
                roleMenuService.writeRoleIds(request.blockedRoleIds()),
                normalizeMaxSelectable(request));
        roleMenuConfigRepository.save(config);

        Guild guild = discordBotService.requireGuild(request.guildId());
        roleMenuService.postOrUpdateMenu(guild, config, null, null);
        return toResponse(config);
    }

    @ManagerAccess
    @PatchMapping("/{id}")
    @Transactional
    public RoleMenuConfigResponse updateConfig(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestBody RoleMenuConfigRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        validate(request);
        RoleMenuConfig config = roleMenuConfigRepository.findByIdAndGuildId(id, request.guildId())
                .orElseThrow(RoleMenuConfigNotFoundException::create);

        String previousChannelId = config.getChannelId();
        String previousMessageId = config.getMessageId();
        config.update(
                request.channelId(), request.configName(), request.title(), request.description(),
                request.uiType(), request.messageType(),
                Boolean.TRUE.equals(request.requireVerified()),
                roleMenuService.writeOptions(request.options()),
                roleMenuService.writeRoleIds(request.allowedRoleIds()),
                roleMenuService.writeRoleIds(request.blockedRoleIds()),
                normalizeMaxSelectable(request));

        Guild guild = discordBotService.requireGuild(request.guildId());
        roleMenuService.postOrUpdateMenu(guild, config, previousChannelId, previousMessageId);
        return toResponse(config);
    }

    @ManagerAccess
    @PostMapping("/{id}/repost")
    @Transactional
    public RoleMenuConfigResponse repost(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        RoleMenuConfig config = roleMenuConfigRepository.findByIdAndGuildId(id, guildId)
                .orElseThrow(RoleMenuConfigNotFoundException::create);
        Guild guild = discordBotService.requireGuild(guildId);
        roleMenuService.postOrUpdateMenu(guild, config, config.getChannelId(), null);
        return toResponse(config);
    }

    @ManagerAccess
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Boolean> deleteConfig(
            @AuthenticationPrincipal Claims claims, @PathVariable Long id, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        RoleMenuConfig config = roleMenuConfigRepository.findByIdAndGuildId(id, guildId)
                .orElseThrow(RoleMenuConfigNotFoundException::create);
        discordBotService.jda().map(jda -> jda.getGuildById(guildId))
                .ifPresent(guild -> roleMenuService.deleteMenuMessage(guild, config));
        roleMenuConfigRepository.deleteByIdAndGuildId(id, guildId);
        return Map.of("success", true);
    }

    private void validate(RoleMenuConfigRequest request) {
        if (request.channelId() == null || request.channelId().isBlank()) {
            throw InvalidRequestException.withMessage("Choose a channel for the role menu.");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw InvalidRequestException.withMessage("Give the role menu a title.");
        }
        if (!"BUTTONS".equals(request.uiType()) && !"SELECT_MENU".equals(request.uiType())) {
            throw InvalidRequestException.withMessage("uiType must be BUTTONS or SELECT_MENU.");
        }
        if (!VALID_MESSAGE_TYPES.contains(request.messageType())) {
            throw InvalidRequestException.withMessage("messageType must be one of " + VALID_MESSAGE_TYPES + ".");
        }
        if (request.maxSelectable() != null && request.maxSelectable() < 1) {
            throw InvalidRequestException.withMessage("Max selectable roles must be at least 1.");
        }
    }

    /** Only meaningful for NORMAL/VERIFY - UNIQUE/BINDING already cap at one and DROP never adds,
     *  so ignore any stray value for those message types. */
    private Integer normalizeMaxSelectable(RoleMenuConfigRequest request) {
        return "NORMAL".equals(request.messageType()) || "VERIFY".equals(request.messageType())
                ? request.maxSelectable() : null;
    }

    private RoleMenuConfigResponse toResponse(RoleMenuConfig config) {
        return new RoleMenuConfigResponse(
                config.getId(), config.getGuildId(), config.getChannelId(), config.getMessageId(),
                config.getConfigName(), config.getTitle(), config.getDescription(), config.getUiType(), config.getMessageType(),
                config.isRequireVerified(), roleMenuService.readOptions(config.getOptions()),
                roleMenuService.readRoleIds(config.getAllowedRoleIds()),
                roleMenuService.readRoleIds(config.getBlockedRoleIds()),
                config.getMaxSelectable());
    }

    public record SetEnabledRequest(String guildId, Boolean enabled) {
    }

    public record RoleMenuConfigRequest(
            String guildId,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("config_name") String configName,
            String title,
            String description,
            @JsonProperty("ui_type") String uiType,
            @JsonProperty("message_type") String messageType,
            @JsonProperty("require_verified") Boolean requireVerified,
            List<RoleMenuOption> options,
            @JsonProperty("allowed_role_ids") List<String> allowedRoleIds,
            @JsonProperty("blocked_role_ids") List<String> blockedRoleIds,
            @JsonProperty("max_selectable") Integer maxSelectable) {
    }

    public record RoleMenuConfigResponse(
            Long id,
            @JsonProperty("guild_id") String guildId,
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("message_id") String messageId,
            @JsonProperty("config_name") String configName,
            String title,
            String description,
            @JsonProperty("ui_type") String uiType,
            @JsonProperty("message_type") String messageType,
            @JsonProperty("require_verified") boolean requireVerified,
            List<RoleMenuOption> options,
            @JsonProperty("allowed_role_ids") List<String> allowedRoleIds,
            @JsonProperty("blocked_role_ids") List<String> blockedRoleIds,
            @JsonProperty("max_selectable") Integer maxSelectable) {
    }
}
