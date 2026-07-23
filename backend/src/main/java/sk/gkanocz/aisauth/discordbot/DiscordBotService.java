package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import sk.gkanocz.aisauth.automod.AutoDeleteListener;
import sk.gkanocz.aisauth.automod.HackedAccountTrapListener;
import sk.gkanocz.aisauth.ticket.TicketButtonListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static net.dv8tion.jda.api.interactions.commands.build.Commands.slash;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordBotService implements ApplicationRunner {

    private final DiscordBotProperties discordBotProperties;
    private final CommandInteractionListener commandInteractionListener;
    private final GuildLifecycleListener guildLifecycleListener;
    private final AutoDeleteListener autoDeleteListener;
    private final HackedAccountTrapListener hackedAccountTrapListener;
    private final TicketButtonListener ticketButtonListener;

    private volatile JDA jda;

    public Optional<JDA> jda() {
        return Optional.ofNullable(jda);
    }

    /**
     * Fetches a guild the bot is a member of, or throws a 503/404-mapped DomainException.
     * Shared by every controller that needs live Discord data for a specific guild.
     */
    public Guild requireGuild(String guildId) {
        Guild guild = jda().orElseThrow(GuildNotAvailableException::botNotConnected).getGuildById(guildId);
        if (guild == null) {
            throw GuildNotAvailableException.guildNotFound(guildId);
        }
        return guild;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!StringUtils.hasText(discordBotProperties.token())) {
            log.warn("app.discord-bot.token nie je nastavené - Discord bot sa nespúšťa");
            return;
        }

        jda = JDABuilder.createLight(discordBotProperties.token())
                .addEventListeners(commandInteractionListener, guildLifecycleListener, autoDeleteListener,
                        hackedAccountTrapListener, ticketButtonListener)
                .build()
                .awaitReady();

        jda.getPresence().setActivity(Activity.playing("/verify & /code"));
        updateAvatar();
        registerCommands();
    }

    private void updateAvatar() {
        try (InputStream avatar = new ClassPathResource("discord/avatar.png").getInputStream()) {
            jda.getSelfUser().getManager().setAvatar(Icon.from(avatar)).queue();
        } catch (IOException e) {
            log.warn("Nepodarilo sa nastaviť avatar bota", e);
        }
    }

    private void registerCommands() {
        List<SlashCommandData> commands = List.of(
                slash("verify", "Over svoje AIS ID")
                        .addOption(OptionType.STRING, "ais_id", "Tvoje AIS ID", true),
                slash("code", "Zadaj verifikačný kód z mailu")
                        .addOption(OptionType.STRING, "code", "Kód z mailu", true),
                slash("find", "Find a verified user by their AIS ID")
                        .addOption(OptionType.STRING, "ais_id", "AIS ID to search for", true),
                slash("manualverify", "Manually verify a user by their Discord mention and AIS ID")
                        .addOption(OptionType.USER, "user", "The Discord user to verify", true)
                        .addOption(OptionType.STRING, "ais_id", "The AIS ID to assign to this user", true)
                        .addOption(OptionType.STRING, "email", "Email address of the user", true),
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
