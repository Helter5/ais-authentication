package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * One-time migration of the pre-V12 "recap_channel_wipe_<guildId>"/"recap_channel_semester_<guildId>"
 * admin_settings rows into log_channel_subscription. Those keys predate LogRoutingService and lived
 * in the generic key/value store (unlike the other three fixed guild_settings columns, which V12
 * could backfill in plain SQL) because their guildId is baked into the key itself rather than a
 * column, so parsing them is easiest done here in Java. Runs once per row; becomes a no-op as soon
 * as the old rows are gone.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class RecapChannelBackfillRunner implements ApplicationRunner {

    private static final String WIPE_PREFIX = "recap_channel_wipe_";
    private static final String SEMESTER_PREFIX = "recap_channel_semester_";

    private final AdminSettingRepository adminSettingRepository;
    private final LogChannelSubscriptionRepository logChannelSubscriptionRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrate(WIPE_PREFIX, LogEventType.WIPE_RECAP);
        migrate(SEMESTER_PREFIX, LogEventType.SEMESTER_RECAP);
    }

    private void migrate(String prefix, LogEventType eventType) {
        adminSettingRepository.findByKeyStartingWith(prefix).forEach(setting -> {
            String guildId = setting.getKey().substring(prefix.length());
            String channelId;
            try {
                channelId = objectMapper.readValue(setting.getValue(), String.class);
            } catch (Exception e) {
                log.error("RecapChannelBackfill: failed to parse {}: {}", setting.getKey(), e.getMessage());
                adminSettingRepository.delete(setting);
                return;
            }

            if (channelId != null && logChannelSubscriptionRepository.findByGuildIdAndEventType(guildId, eventType).isEmpty()) {
                logChannelSubscriptionRepository.save(new LogChannelSubscription(guildId, channelId, eventType));
                log.info("RecapChannelBackfill: migrated {} -> {} ({})", setting.getKey(), eventType, channelId);
            }
            adminSettingRepository.delete(setting);
        });
    }
}
