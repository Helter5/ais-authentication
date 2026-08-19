package sk.gkanocz.aisauth.thesiscounter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThesisCounterConfigRepository extends JpaRepository<ThesisCounterConfig, Long> {

    List<ThesisCounterConfig> findByGuildIdOrderByCreatedAtAsc(String guildId);

    Optional<ThesisCounterConfig> findByIdAndGuildId(Long id, String guildId);

    Optional<ThesisCounterConfig> findByGuildIdAndChannelId(String guildId, String channelId);

    List<ThesisCounterConfig> findByActiveTrue();

    void deleteByIdAndGuildId(Long id, String guildId);
}
