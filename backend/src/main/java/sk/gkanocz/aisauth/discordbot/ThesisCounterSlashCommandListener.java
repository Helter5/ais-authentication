package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.shared.DomainException;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterConfig;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterConfigRepository;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterService;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
class ThesisCounterSlashCommandListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ThesisCounterService thesisCounterService;
    private final ThesisCounterConfigRepository thesisCounterConfigRepository;
    private final AdminSettingsService adminSettingsService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * {@code odpocet} has two subcommands with independently useful ephemeral defaults ("add" is
     * usually run in front of others so it stays non-ephemeral by default, "list" is a personal
     * lookup so it defaults ephemeral) - one shared {@code ephemeralOverride} boolean (the
     * convention every other command's Settings modal uses) can't express both, so this reads its
     * own {@code subcommandEphemeral: {add, list}} object out of the same
     * {@code cmd_settings_<guildId>_odpocet} blob instead (small per-subcommand table on the
     * dashboard's /odpocet Settings modal). The central {@code ephemeralOverride} param is
     * intentionally unused here.
     */
    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        if (!"odpocet".equals(event.getName())) {
            return;
        }
        Map<String, Object> settings = adminSettingsService.get(
                "cmd_settings_" + event.getGuild().getId() + "_odpocet", new TypeReference<Map<String, Object>>() { }, Map.of());
        Object rawSubcommandEphemeral = settings.get("subcommandEphemeral");
        Map<?, ?> subcommandEphemeral = rawSubcommandEphemeral instanceof Map<?, ?> m ? m : Map.of();
        boolean ephemeralAdd = subcommandEphemeral.get("add") instanceof Boolean b ? b : false;
        boolean ephemeralList = subcommandEphemeral.get("list") instanceof Boolean b ? b : true;

        switch (event.getSubcommandName()) {
            case "add" -> handleAdd(event, ephemeralAdd);
            case "list" -> handleList(event, ephemeralList);
            default -> {
            }
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event, boolean ephemeral) {
        event.deferReply(ephemeral).queue();

        GuildChannelUnion channel = event.getOption("miestnost").getAsChannel();
        String label = event.getOption("typ").getAsString();
        String rawDate = event.getOption("datum").getAsString();

        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(rawDate, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            event.getHook().sendMessage("Neplatný formát dátumu. Použi dd.MM.yyyy, napríklad 15.06.2027.").queue();
            return;
        }
        String nameFormat = optionOrNull(event, "format");
        String todayFormat = optionOrNull(event, "format_dnes");

        try {
            thesisCounterService.addCounter(event.getGuild(), channel.getId(), label, targetDate, nameFormat, todayFormat);
            event.getHook().sendMessage(
                    "Odpočet pridaný pre <#" + channel.getId() + "> (" + label + ", " + rawDate + ")."
                            + dashboardHint()).queue();
        } catch (DomainException e) {
            event.getHook().sendMessage(e.getMessage() + dashboardHint()).queue();
        } catch (Exception e) {
            log.error("odpocet add failed", e);
            event.getHook().sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.").queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event, boolean ephemeral) {
        event.deferReply(ephemeral).queue();

        List<ThesisCounterConfig> configs = thesisCounterConfigRepository
                .findByGuildIdOrderByCreatedAtAsc(event.getGuild().getId());

        if (configs.isEmpty()) {
            event.getHook().sendMessage("Žiadne nastavené odpočty." + dashboardHint()).queue();
            return;
        }

        String body = configs.stream()
                .map(c -> {
                    String status = c.isActive()
                            ? thesisCounterService.daysRemaining(c) + " dní zostáva"
                            : "ukončený";
                    return "<#" + c.getChannelId() + "> — " + c.getLabel() + " — " + c.getTargetDate() + " — " + status;
                })
                .collect(Collectors.joining("\n"));
        event.getHook().sendMessage(body + dashboardHint()).queue();
    }

    /** Editing, removing and custom name formats are dashboard-only - Discord slash options can't
     *  express "leave this field unchanged", so a partial-update UX belongs in the web form instead. */
    private String dashboardHint() {
        return "\n-# Úpravu, odstránenie a vlastný formát názvu nastavíš v dashboarde: " + frontendUrl + "/modules/thesiscounter";
    }

    private String optionOrNull(SlashCommandInteractionEvent event, String name) {
        var option = event.getOption(name);
        return option == null ? null : option.getAsString();
    }
}
