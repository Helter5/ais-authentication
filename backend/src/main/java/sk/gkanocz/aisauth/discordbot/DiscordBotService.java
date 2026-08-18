package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import sk.gkanocz.aisauth.automod.AutoDeleteListener;
import sk.gkanocz.aisauth.automod.AutoMentionListener;
import sk.gkanocz.aisauth.automod.HackedAccountTrapListener;
import sk.gkanocz.aisauth.rolemenu.RoleMenuInteractionListener;
import sk.gkanocz.aisauth.verification.DatabaseSyncService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static net.dv8tion.jda.api.interactions.commands.build.Commands.slash;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordBotService implements ApplicationRunner {

    /**
     * Discord-level floor for moderation/PII-bearing commands (/manualverify, /warn, /info,
     * /user) - locked to administrators by default at command-DEFINITION time, not just via the
     * runtime CommandPermissions blob (which defaults to CommandPermissions.empty(), i.e. wide
     * open, until a super-admin visits the Commands dashboard page and explicitly restricts +
     * redeploys it). Without this, every freshly-added guild lets any member run /manualverify to
     * self-grant the verified role (manuallyVerify() skips the LDAP eligibility check entirely),
     * /warn clearall to erase another member's warning history, or /user to read another member's
     * AIS ID, email and warning history straight out of the box.
     */
    private static final DefaultMemberPermissions ADMIN_ONLY = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR);

    private final DiscordBotProperties discordBotProperties;
    private final CommandInteractionListener commandInteractionListener;
    private final GuildLifecycleListener guildLifecycleListener;
    private final AutoDeleteListener autoDeleteListener;
    private final AutoMentionListener autoMentionListener;
    private final HackedAccountTrapListener hackedAccountTrapListener;
    private final RoleMenuInteractionListener roleMenuInteractionListener;
    private final VerifyConfirmationButtonListener verifyConfirmationButtonListener;
    private final SubjectRoleButtonListener subjectRoleButtonListener;
    private final GuildAllowlistEventManager guildAllowlistEventManager;
    private final DatabaseSyncService databaseSyncService;

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
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setEventManager(guildAllowlistEventManager)
                .addEventListeners(commandInteractionListener, guildLifecycleListener, autoDeleteListener,
                        autoMentionListener, hackedAccountTrapListener, roleMenuInteractionListener,
                        verifyConfirmationButtonListener, subjectRoleButtonListener)
                .build()
                .awaitReady();

        jda.getPresence().setActivity(Activity.playing("/verify & /code"));
        updateAvatar();
        registerCommands();
        databaseSyncService.syncAllGuildsAsync(jda.getGuilds());
    }

    private void updateAvatar() {
        try (InputStream avatar = new ClassPathResource("discord/avatar.png").getInputStream()) {
            jda.getSelfUser().getManager().setAvatar(Icon.from(avatar)).queue();
        } catch (IOException e) {
            log.warn("Nepodarilo sa nastaviť avatar bota", e);
        }
    }

    /**
     * The canonical command list - single source of truth for both startup registration and
     * the dashboard's "Sync Visibility" redeploy. Reading commands back via jda.retrieveCommands()
     * would return Discord's *global* application commands, which this bot never registers
     * (commands go to a single fixed guild below), so that lookup is always empty; anything
     * built from it and pushed with updateCommands() wipes the guild's commands instead of
     * syncing them.
     */
    public List<SlashCommandData> baseCommands() {
        return List.of(
                slash("verify", "Over svoje AIS ID")
                        .addOption(OptionType.STRING, "ais_id", "Tvoje AIS ID", true),
                slash("code", "Zadaj verifikačný kód z mailu")
                        .addOption(OptionType.STRING, "code", "Kód z mailu", true),
                slash("find", "Find a verified user by their AIS ID")
                        .addOption(OptionType.STRING, "ais_id", "AIS ID to search for", true),
                slash("manualverify", "Manually verify a user by their Discord mention and AIS ID")
                        .addOption(OptionType.USER, "user", "The Discord user to verify", true)
                        .addOption(OptionType.STRING, "ais_id", "The AIS ID to assign to this user", true)
                        .addOption(OptionType.STRING, "email", "Email address of the user", true)
                        .setDefaultPermissions(ADMIN_ONLY),
                slash("warn", "Manage warnings for a user")
                        .addSubcommands(
                                new SubcommandData("add", "Warn a user")
                                        .addOption(OptionType.USER, "user", "User to warn", true)
                                        .addOption(OptionType.STRING, "reason", "Reason for the warning", true),
                                new SubcommandData("remove", "Remove a specific warning by its ID")
                                        .addOption(OptionType.INTEGER, "id", "Warning ID to remove", true),
                                new SubcommandData("clearall", "Clear all warnings for a user")
                                        .addOption(OptionType.USER, "user", "User to clear warnings for", true),
                                new SubcommandData("list", "View warnings - for a specific user or all warnings in this server")
                                        .addOption(OptionType.USER, "user", "User to check warnings for (omit for all)", false))
                        .setDefaultPermissions(ADMIN_ONLY),
                slash("mywarns", "Show your own warnings on this server"),
                slash("info", "Show bot configuration and server information")
                        .setDefaultPermissions(ADMIN_ONLY),
                slash("user", "Show detailed info about a server member")
                        .addOption(OptionType.USER, "user", "Member to inspect", true)
                        .setDefaultPermissions(ADMIN_ONLY),
                slash("addpredmet", "Priraď si rolu predmetu, ktorý opakuješ alebo máš navyše zapísaný")
                        .addOption(OptionType.STRING, "predmety",
                                "Kódy predmetov oddelené medzerou alebo čiarkou (napr. mat1 zfi)", true)
        );
    }

    private void registerCommands() {
        List<SlashCommandData> commands = baseCommands();
        List<String> guildIds = discordBotProperties.guildIds();

        if (guildIds.isEmpty()) {
            jda.updateCommands().addCommands(commands).queue();
            log.info("Slash commandy zaregistrované globálne (prejavia sa do ~1h)");
            return;
        }

        for (String guildId : guildIds) {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                log.warn("Bot nie je na guilde {}, preskakujem registráciu commandov", guildId);
                continue;
            }
            guild.updateCommands().addCommands(commands).queue();
            log.info("Slash commandy zaregistrované na guild {}", guildId);
        }
    }
}
