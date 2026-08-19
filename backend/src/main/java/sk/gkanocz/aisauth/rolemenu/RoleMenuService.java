package sk.gkanocz.aisauth.rolemenu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Service;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds and (re)posts the actual Discord message for a role menu - the embed plus its buttons
 * or select-menu component - shared between the dashboard CRUD (initial post / repost after an
 * edit) and nothing else; the actual click/selection handling lives in RoleMenuInteractionListener.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleMenuService {

    static final int MAX_OPTIONS = 25;
    private static final int MAX_BUTTONS_PER_ROW = 5;

    private final ObjectMapper objectMapper;

    /**
     * Reads options via a generic Map so a pre-multi-role menu's legacy {@code {"role_id": "..."}}
     * shape (single role per option) still loads fine as a one-role bundle, instead of every role
     * menu saved before this feature failing to deserialize.
     */
    @SuppressWarnings("unchecked")
    List<RoleMenuOption> readOptions(String json) {
        List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        List<RoleMenuOption> options = new ArrayList<>();
        for (Map<String, Object> node : raw) {
            List<String> roleIds;
            if (node.get("role_ids") instanceof List<?> list) {
                roleIds = list.stream().map(String::valueOf).toList();
            } else if (node.get("role_id") instanceof String single) {
                roleIds = List.of(single);
            } else {
                roleIds = List.of();
            }
            options.add(new RoleMenuOption(
                    roleIds,
                    (String) node.get("label"),
                    (String) node.get("emoji"),
                    (String) node.get("description")));
        }
        return options;
    }

    String writeOptions(List<RoleMenuOption> options) {
        if (options == null || options.isEmpty()) {
            throw InvalidRequestException.withMessage("Add at least one role.");
        }
        if (options.size() > MAX_OPTIONS) {
            throw InvalidRequestException.withMessage("A role menu can have at most " + MAX_OPTIONS + " roles.");
        }
        if (options.stream().anyMatch(o -> o.roleIds() == null || o.roleIds().isEmpty())) {
            throw InvalidRequestException.withMessage("Every role option needs at least one Discord role.");
        }
        return objectMapper.writeValueAsString(options);
    }

    List<String> readRoleIds(String json) {
        return objectMapper.readValue(json, new TypeReference<List<String>>() { });
    }

    String writeRoleIds(List<String> roleIds) {
        return objectMapper.writeValueAsString(roleIds == null ? List.of() : roleIds);
    }

    /**
     * Runs synchronously (JDA .complete() rather than .queue()) so config.setMessageId() happens
     * before this method returns - it's always called from within a @Transactional controller
     * method, and a .queue() callback would fire on a JDA thread after that transaction (and the
     * HTTP request) already completed, meaning the new message ID would never actually get
     * persisted and every edit would look like it needs a fresh post again.
     */
    public void postOrUpdateMenu(Guild guild, RoleMenuConfig config, String previousChannelId, String previousMessageId) {
        TextChannel channel = guild.getTextChannelById(config.getChannelId());
        if (channel == null) {
            throw InvalidRequestException.withMessage("Channel not found.");
        }

        boolean channelChanged = previousChannelId != null && !previousChannelId.equals(config.getChannelId());
        if (channelChanged && previousMessageId != null) {
            deleteMessageBestEffort(guild, previousChannelId, previousMessageId);
        }
        String editTargetMessageId = channelChanged ? null : previousMessageId;

        EmbedBuilder embed = buildEmbed(config);
        List<ActionRow> rows = buildComponents(config);

        if (editTargetMessageId != null) {
            try {
                channel.editMessageEmbedsById(editTargetMessageId, embed.build()).setComponents(rows).complete();
                return;
            } catch (Exception e) {
                log.warn("Role menu {}: failed to edit message {}, posting a new one instead",
                        config.getId(), editTargetMessageId, e);
            }
        }
        postFresh(channel, embed, rows, config);
    }

    public void deleteMenuMessage(Guild guild, RoleMenuConfig config) {
        if (config.getMessageId() != null) {
            deleteMessageBestEffort(guild, config.getChannelId(), config.getMessageId());
        }
    }

    private void postFresh(TextChannel channel, EmbedBuilder embed, List<ActionRow> rows, RoleMenuConfig config) {
        Message message = channel.sendMessageEmbeds(embed.build()).setComponents(rows).complete();
        config.setMessageId(message.getId());
    }

    private void deleteMessageBestEffort(Guild guild, String channelId, String messageId) {
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        channel.deleteMessageById(messageId).queue(s -> { }, f -> { });
    }

    private EmbedBuilder buildEmbed(RoleMenuConfig config) {
        return new EmbedBuilder()
                .setColor(0x5865F2)
                .setTitle(config.getTitle())
                .setDescription(config.getDescription());
    }

    private List<ActionRow> buildComponents(RoleMenuConfig config) {
        List<RoleMenuOption> options = readOptions(config.getOptions());
        if ("SELECT_MENU".equals(config.getUiType())) {
            boolean singlePick = "UNIQUE".equals(config.getMessageType()) || "BINDING".equals(config.getMessageType());
            int maxValues = singlePick ? 1
                    : Math.min(options.size(), config.getMaxSelectable() != null ? config.getMaxSelectable() : options.size());
            StringSelectMenu.Builder builder = StringSelectMenu.create("rolemenu:" + config.getId())
                    .setPlaceholder("Choose your role" + (options.size() > 1 ? "(s)" : ""))
                    .setMinValues(0)
                    .setMaxValues(maxValues);
            for (int i = 0; i < options.size(); i++) {
                RoleMenuOption option = options.get(i);
                String value = String.valueOf(i);
                if (option.emoji() != null && !option.emoji().isBlank()) {
                    builder.addOption(option.label(), value,
                            option.description() == null ? "" : option.description(), Emoji.fromFormatted(option.emoji()));
                } else {
                    builder.addOption(option.label(), value, option.description() == null ? "" : option.description());
                }
            }
            return List.of(ActionRow.of(builder.build()));
        }

        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            RoleMenuOption option = options.get(i);
            String customId = "rolemenu:" + config.getId() + ":" + i;
            Button button = option.emoji() != null && !option.emoji().isBlank()
                    ? Button.secondary(customId, option.label()).withEmoji(Emoji.fromFormatted(option.emoji()))
                    : Button.secondary(customId, option.label());
            buttons.add(button);
        }
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += MAX_BUTTONS_PER_ROW) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + MAX_BUTTONS_PER_ROW, buttons.size()))));
        }
        return rows;
    }
}
