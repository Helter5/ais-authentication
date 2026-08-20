package sk.gkanocz.aisauth.rolesnapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RoleSnapshotRepository extends JpaRepository<RoleSnapshot, Long> {

    Optional<RoleSnapshot> findByGuildIdAndDiscordId(String guildId, String discordId);

    void deleteByGuildIdAndDiscordId(String guildId, String discordId);

    void deleteByExpiresAtBefore(LocalDateTime instant);
}
