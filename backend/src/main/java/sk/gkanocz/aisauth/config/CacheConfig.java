package sk.gkanocz.aisauth.config;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.gkanocz.aisauth.settings.AdminSetting;
import sk.gkanocz.aisauth.settings.AdminSettingRepository;

import java.time.Duration;

/**
 * The {@code adminSettings} cache is read on the slash-command hot path <em>before</em> the Discord
 * interaction is acknowledged, so a synchronous DB hit there (Postgres down or blipping) blocks
 * ~10s on the dead connection pool and blows Discord's 3s deadline.
 *
 * <p>{@code refreshAfterWrite} is what makes that safe: once a key has been loaded, a read always
 * returns the cached value <em>immediately</em> and only reloads in the background - and if that
 * reload fails, Caffeine keeps serving the stale value. So any outage shorter than
 * {@code expireAfterWrite} is invisible to the gating path for every key already seen once - and
 * {@code AdminSettingsCacheWarmer} loads them all at startup, while the DB is still guaranteed up
 * from the Flyway migration that just ran.
 *
 * <p>Guarded on {@code spring.cache.type=caffeine} so the test suite (which sets it to {@code none})
 * gets Spring Boot's no-op manager and never caches across its reused context.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine")
public class CacheConfig {

    /** Loader sentinel for "no row" - admin setting values are always JSON, never blank. */
    static final String ABSENT = "";

    private final AdminSettingRepository adminSettingRepository;

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                // 24h is only a backstop against a genuinely dead key lingering forever; the 30s
                // refresh is what keeps entries current, and it hands back the stale value (rather
                // than evicting) whenever the reload fails - so a multi-hour Postgres outage never
                // forces the gating path back onto the database.
                .expireAfterWrite(Duration.ofHours(24))
                .refreshAfterWrite(Duration.ofSeconds(30)));
        manager.setCacheLoader(new CacheLoader<Object, Object>() {
            /** Cold key. If the DB is down, serve it as absent so callers fall back to their defaults
             *  instead of every message/interaction listener throwing an uncaught exception; the
             *  background refresh corrects it once Postgres is back. */
            @Override
            public Object load(Object key) {
                try {
                    return read(key);
                } catch (RuntimeException e) {
                    log.debug("adminSettings cache: DB unavailable loading '{}', serving as absent ({})", key, e.getMessage());
                    return ABSENT;
                }
            }

            /** Background refresh of an entry that already has a value. On a DB failure keep whatever
             *  is currently cached (a good value stays good, an ABSENT stays ABSENT) instead of
             *  overwriting it or spamming Caffeine's own refresh-failure logger; the next refresh
             *  after Postgres is back picks up the real value. */
            @Override
            public Object reload(Object key, Object oldValue) {
                try {
                    return read(key);
                } catch (RuntimeException e) {
                    return oldValue;
                }
            }

            private Object read(Object key) {
                return adminSettingRepository.findById((String) key).map(AdminSetting::getValue).orElse(ABSENT);
            }
        });
        return manager;
    }
}
