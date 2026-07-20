package sk.gkanocz.aisauth.warn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarnRepository extends JpaRepository<Warn, Long> {

    List<Warn> findByDiscordIdAndGuildIdOrderByCreatedAtDesc(String discordId, String guildId);

    List<Warn> findByGuildIdOrderByCreatedAtDesc(String guildId);

    long countByDiscordIdAndGuildId(String discordId, String guildId);

    Optional<Warn> findByIdAndGuildId(Long id, String guildId);

    void deleteByDiscordIdAndGuildId(String discordId, String guildId);
}
