package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cached read-through over {@link AdminSettingRepository}, kept as its own bean so the
 * {@code @Cacheable} proxy actually fires - a self-invocation from inside {@link AdminSettingsService}
 * would bypass it. Caches the raw JSON string (and misses, as {@code null}) per key, not the
 * deserialized value: {@link AdminSettingsService#get} reads the same key with different target
 * types, so deserialization has to stay per-call.
 *
 * <p>Why cache at all: every slash command runs ~5 of these lookups (guild allowlist, maintenance
 * mode, per-guild command state/permissions/settings) on the hot path <em>before</em> it can
 * acknowledge the Discord interaction. A brief Postgres hiccup - a restart, a network blip, HikariCP
 * recycling a batch of dead connections - then blocks each lookup for up to the 10s
 * connection-timeout, blowing Discord's 3s deadline and surfacing as "The application did not
 * respond". A short TTL keeps the hot path off the database entirely in the common case and rides
 * out any blip shorter than the TTL; dashboard writes evict their key immediately via
 * {@link AdminSettingsService}, so configuration changes still apply without waiting for it.
 */
@Component
@RequiredArgsConstructor
public class AdminSettingReader {

    static final String CACHE = "adminSettings";

    private final AdminSettingRepository adminSettingRepository;

    @Cacheable(cacheNames = CACHE, key = "#key")
    public String rawValueOrNull(String key) {
        return adminSettingRepository.findById(key).map(AdminSetting::getValue).orElse(null);
    }

    /** Called by {@link AdminSettingsService} after a write so the next read reflects it immediately. */
    @CacheEvict(cacheNames = CACHE, key = "#key")
    public void evict(String key) {
        // no body - the annotation does the work; a method is needed so the cache proxy has a join point
    }
}
