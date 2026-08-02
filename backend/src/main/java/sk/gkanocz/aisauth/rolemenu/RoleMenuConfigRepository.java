package sk.gkanocz.aisauth.rolemenu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleMenuConfigRepository extends JpaRepository<RoleMenuConfig, Long> {

    List<RoleMenuConfig> findByGuildIdOrderByCreatedAtAsc(String guildId);

    Optional<RoleMenuConfig> findByIdAndGuildId(Long id, String guildId);

    Optional<RoleMenuConfig> findByGuildIdAndMessageId(String guildId, String messageId);

    void deleteByIdAndGuildId(Long id, String guildId);
}
