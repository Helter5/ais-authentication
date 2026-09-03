package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.AdminSettingReader;

/**
 * Loads every admin-settings key the slash-command gating path reads into the cache at startup,
 * while the DB is still guaranteed reachable (the Flyway migration has just run). Combined with the
 * cache's {@code refreshAfterWrite} (see {@code CacheConfig}), this keeps
 * {@link CommandInteractionListener} off the database entirely during a later Postgres outage - so
 * an interaction is still acknowledged inside Discord's 3s window instead of blocking ~10s on the
 * dead connection pool and surfacing as "The application did not respond".
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AdminSettingsCacheWarmer implements ApplicationRunner {

    private final AdminSettingReader adminSettingReader;
    private final DiscordBotProperties discordBotProperties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            adminSettingReader.rawValue("allowed_guild_ids");
            adminSettingReader.rawValue("maintenance_mode");
            for (String guildId : discordBotProperties.guildIds()) {
                adminSettingReader.rawValue("cmd_states_" + guildId);
                for (String command : CommandInteractionListener.KNOWN_COMMANDS) {
                    adminSettingReader.rawValue("cmd_perms_" + guildId + "_" + command);
                    adminSettingReader.rawValue("cmd_settings_" + guildId + "_" + command);
                }
            }
            log.info("Admin-settings gating cache warmed");
        } catch (Exception e) {
            log.warn("Admin-settings cache warm-up failed - keys will load lazily: {}", e.getMessage());
        }
    }
}
