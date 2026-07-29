package sk.gkanocz.aisauth.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LogChannelSubscriptionRepository extends JpaRepository<LogChannelSubscription, Long> {

    List<LogChannelSubscription> findByGuildId(String guildId);

    Optional<LogChannelSubscription> findByGuildIdAndEventType(String guildId, LogEventType eventType);

    void deleteByGuildIdAndEventType(String guildId, LogEventType eventType);
}
