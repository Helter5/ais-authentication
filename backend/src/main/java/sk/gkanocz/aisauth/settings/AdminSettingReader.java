package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cached read-through over {@link AdminSettingRepository}, kept as its own bean so the
 * {@code @Cacheable} proxy actually fires - a self-invocation from inside {@link AdminSettingsService}
 * would bypass it. Caches the raw JSON string per key (an empty string means "no row" - admin
 * setting values are always JSON, never blank); deserialization stays per-call because
 * {@link AdminSettingsService#get} reads the same key with different target types.
 *
 * <p>Why cache at all: every slash command runs ~5 of these lookups (guild allowlist, maintenance
 * mode, per-guild command state/permissions/settings) on the hot path <em>before</em> it can
 * acknowledge the Discord interaction. See {@code CacheConfig} for how {@code refreshAfterWrite}
 * keeps that path off the database during a Postgres outage, and {@code AdminSettingsCacheWarmer}
 * for the startup pre-load that makes sure no gating key is ever cold when it matters.
 */
@Component
@RequiredArgsConstructor
public class AdminSettingReader {

    static final String CACHE = "adminSettings";

    private final AdminSettingRepository adminSettingRepository;

    /** Raw stored JSON for {@code key}, or {@code ""} if there is no such row. */
    @Cacheable(cacheNames = CACHE, key = "#key")
    public String rawValue(String key) {
        return adminSettingRepository.findById(key).map(AdminSetting::getValue).orElse("");
    }

    /** Called by {@link AdminSettingsService} after a write so the next read reflects it immediately. */
    @CacheEvict(cacheNames = CACHE, key = "#key")
    public void evict(String key) {
        // no body - the annotation does the work; a method is needed so the cache proxy has a join point
    }
}
