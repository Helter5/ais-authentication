package sk.gkanocz.aisauth.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    
    Optional<VerificationCode> findByDiscordIdAndGuildIdAndExpiresAtAfter(String discordId, String guildId, LocalDateTime expiresAt);

    void deleteByDiscordIdAndGuildId(String discordId, String guildId);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
