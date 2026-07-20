package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static net.dv8tion.jda.api.interactions.commands.build.Commands.slash;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordBotService implements ApplicationRunner {

    private final DiscordBotProperties discordBotProperties;
    private final VerificationSlashCommandListener verificationSlashCommandListener;
    private final WarnSlashCommandListener warnSlashCommandListener;

    private volatile JDA jda;

    public Optional<JDA> jda() {
        return Optional.ofNullable(jda);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!StringUtils.hasText(discordBotProperties.token())) {
            log.warn("app.discord-bot.token nie je nastavené - Discord bot sa nespúšťa");
            return;
        }

        jda = JDABuilder.createLight(discordBotProperties.token())
                .addEventListeners(verificationSlashCommandListener, warnSlashCommandListener)
                .build()
                .awaitReady();

        registerCommands();
    }

    private void registerCommands() {
        List<SlashCommandData> commands = List.of(
                slash("verify", "Over svoje AIS ID")
                        .addOption(OptionType.STRING, "ais_id", "Tvoje AIS ID", true),
                slash("code", "Zadaj verifikačný kód z mailu")
                        .addOption(OptionType.STRING, "code", "Kód z mailu", true),
                slash("warn", "Warn a user")
                        .addOption(OptionType.USER, "user", "User to warn", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the warning", true),
                slash("warns", "View warnings - for a specific user or all warnings in this server")
                        .addOption(OptionType.USER, "user", "User to check warnings for (omit for all)", false),
                slash("mywarns", "Show your own warnings on this server"),
                slash("removewarn", "Remove a specific warning by its ID")
                        .addOption(OptionType.INTEGER, "id", "Warning ID to remove", true),
                slash("clearwarns", "Clear all warnings for a user")
                        .addOption(OptionType.USER, "user", "User to clear warnings for", true)
        );

        Guild guild = StringUtils.hasText(discordBotProperties.guildId())
                ? jda.getGuildById(discordBotProperties.guildId())
                : null;

        if (guild != null) {
            guild.updateCommands().addCommands(commands).queue();
            log.info("Slash commandy zaregistrované na guild {}", discordBotProperties.guildId());
        } else {
            jda.updateCommands().addCommands(commands).queue();
            log.info("Slash commandy zaregistrované globálne (prejavia sa do ~1h)");
        }
    }
}
