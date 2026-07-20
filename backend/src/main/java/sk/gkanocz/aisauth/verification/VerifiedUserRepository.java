package sk.gkanocz.aisauth.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VerifiedUserRepository extends JpaRepository<VerifiedUser, Long> {

    Optional<VerifiedUser> findByDiscordIdAndGuildId(String discordId, String guildId);

    Optional<VerifiedUser> findByAisIdAndGuildId(String aisId, String guildId);

    List<VerifiedUser> findByGuildId(String guildId);

    boolean existsByDiscordIdAndGuildId(String discordId, String guildId);

    boolean existsByAisIdAndGuildId(String aisId, String guildId);

    void deleteByDiscordIdAndGuildId(String discordId, String guildId);
}
