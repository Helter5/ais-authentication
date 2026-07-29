package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogRoutingService {

    private final LogChannelSubscriptionRepository logChannelSubscriptionRepository;

    public Optional<String> channelIdFor(String guildId, LogEventType eventType) {
        return logChannelSubscriptionRepository.findByGuildIdAndEventType(guildId, eventType)
                .map(LogChannelSubscription::getChannelId);
    }

    public List<LogChannelSubscription> listForGuild(String guildId) {
        return logChannelSubscriptionRepository.findByGuildId(guildId);
    }

    /**
     * Applies the full desired guild<->channel mapping in one load-mutate-save, mirroring
     * GuildSettingsService.updateFields: a caller saving several event-type assignments at once
     * (the dashboard's log-channels form) shouldn't fire N concurrent single-assignment writes
     * against this guild's rows and risk losing one to a racing partial update.
     */
    @Transactional
    public void replaceAll(String guildId, Map<LogEventType, String> assignments) {
        for (LogEventType eventType : LogEventType.values()) {
            String channelId = assignments.get(eventType);
            Optional<LogChannelSubscription> existing =
                    logChannelSubscriptionRepository.findByGuildIdAndEventType(guildId, eventType);

            if (channelId == null || channelId.isBlank()) {
                existing.ifPresent(logChannelSubscriptionRepository::delete);
            } else if (existing.isPresent()) {
                existing.get().setChannelId(channelId);
            } else {
                logChannelSubscriptionRepository.save(new LogChannelSubscription(guildId, channelId, eventType));
            }
        }
    }
}
