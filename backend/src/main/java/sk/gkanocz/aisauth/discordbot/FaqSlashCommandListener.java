package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /faq} - a single static embed, not the old category/question/answer button tree. The
 * whole description is the admin-configured text, same {@code cmd_settings_<guildId>_faq} ->
 * "message" convention every other command's Settings modal already saves through (see
 * {@code VerificationSlashCommandListener}'s {@code codeSuccessMessage}) - nothing hardcoded here,
 * the dashboard is the only source of content. Always ephemeral, regardless of the dashboard's
 * per-command ephemeral override - it's a personal cheat-sheet, not a channel announcement.
 */
@Component
@RequiredArgsConstructor
class FaqSlashCommandListener {

    private static final Pattern CHANNEL_TOKEN = Pattern.compile("\\{channel=(\\d+)}");
    private static final String DEFAULT_MESSAGE = "FAQ nie je nastavené, kontaktuj administrátora.";

    private final AdminSettingsService adminSettingsService;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        if (!"faq".equals(event.getName())) {
            return;
        }
        event.replyEmbeds(buildEmbed(event.getGuild().getId())).setEphemeral(true).queue();
    }

    private MessageEmbed buildEmbed(String guildId) {
        return new EmbedBuilder()
                .setColor(0x5865F2)
                .setTitle("FAQ")
                .setDescription(resolveMessage(guildId))
                .build();
    }

    private String resolveMessage(String guildId) {
        Map<String, Object> settings = adminSettingsService.get(
                "cmd_settings_" + guildId + "_faq", new TypeReference<Map<String, Object>>() { }, Map.of());
        Object configured = settings.get("message");
        if (!(configured instanceof String s) || s.isBlank()) {
            return DEFAULT_MESSAGE;
        }
        Matcher matcher = CHANNEL_TOKEN.matcher(s);
        return matcher.replaceAll(result -> "<#" + result.group(1) + ">");
    }
}
