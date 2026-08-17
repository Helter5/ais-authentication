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
     * Applies only the event types present in the given map, leaving every other event type's
     * routing untouched - each owning command/module page (Warn, Hacked Account Trap,
     * Wipe, Semester Switch) saves its own small subset of event types independently, so this
     * must not wipe out assignments some other page is responsible for.
     */
    @Transactional
    public void upsert(String guildId, Map<LogEventType, String> assignments) {
        assignments.forEach((eventType, channelId) -> {
            Optional<LogChannelSubscription> existing =
                    logChannelSubscriptionRepository.findByGuildIdAndEventType(guildId, eventType);

            if (channelId == null || channelId.isBlank()) {
                existing.ifPresent(logChannelSubscriptionRepository::delete);
            } else if (existing.isPresent()) {
                existing.get().setChannelId(channelId);
            } else {
                logChannelSubscriptionRepository.save(new LogChannelSubscription(guildId, channelId, eventType));
            }
        });
    }
}
