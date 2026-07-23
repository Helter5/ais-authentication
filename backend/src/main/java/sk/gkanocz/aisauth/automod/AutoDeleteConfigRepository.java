package sk.gkanocz.aisauth.automod;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutoDeleteConfigRepository extends JpaRepository<AutoDeleteConfig, Long> {

    List<AutoDeleteConfig> findByGuildIdOrderByCreatedAtAsc(String guildId);

    List<AutoDeleteConfig> findByGuildIdAndChannelId(String guildId, String channelId);

    Optional<AutoDeleteConfig> findByIdAndGuildId(Long id, String guildId);

    void deleteByIdAndGuildId(Long id, String guildId);
}
