package sk.gkanocz.aisauth.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findByDiscordIdAndGuildIdAndExpiresAtAfter(String discordId, String guildId, LocalDateTime expiresAt);

    List<VerificationCode> findByGuildIdOrderByCreatedAtDesc(String guildId);

    /**
     * @Modifying forces an immediate bulk DELETE instead of Spring Data's default derived-delete
     * behavior (load matching entities, queue entityManager.remove(), physically delete at next
     * flush). Without it, VerificationService.initiateVerification's delete-then-save on the same
     * (discord_id, guild_id) unique key hit Hibernate's flush ordering (inserts before deletes in
     * the same flush) - the new row's INSERT ran before the old row's DELETE, violating the
     * verification_codes_discord_id_guild_id_key constraint on every re-verify attempt.
     */
    @Modifying
    @Query("delete from VerificationCode v where v.discordId = :discordId and v.guildId = :guildId")
    void deleteByDiscordIdAndGuildId(String discordId, String guildId);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    long countByExpiresAtAfter(LocalDateTime dateTime);
}
