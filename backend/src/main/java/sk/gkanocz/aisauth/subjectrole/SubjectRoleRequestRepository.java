package sk.gkanocz.aisauth.subjectrole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubjectRoleRequestRepository extends JpaRepository<SubjectRoleRequest, Long> {

    long countByGuildIdAndDiscordIdAndStatusInAndCreatedAtAfter(
            String guildId, String discordId, List<SubjectRoleRequestStatus> statuses, LocalDateTime after);

    Optional<SubjectRoleRequest> findByIdAndGuildId(Long id, String guildId);

    List<SubjectRoleRequest> findByGuildIdAndDiscordIdOrderByCreatedAtDesc(String guildId, String discordId);
}
