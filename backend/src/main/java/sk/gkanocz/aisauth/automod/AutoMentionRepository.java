package sk.gkanocz.aisauth.automod;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutoMentionRepository extends JpaRepository<AutoMention, Long> {

    List<AutoMention> findByGuildIdOrderByCreatedAtAsc(String guildId);

    Optional<AutoMention> findByGuildIdAndChannelId(String guildId, String channelId);

    Optional<AutoMention> findByIdAndGuildId(Long id, String guildId);

    void deleteByIdAndGuildId(Long id, String guildId);
}
