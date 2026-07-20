package sk.gkanocz.aisauth.warn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarnThresholdRepository extends JpaRepository<WarnThreshold, Long> {

    List<WarnThreshold> findByGuildIdOrderByWarnLimitAsc(String guildId);

    Optional<WarnThreshold> findByGuildIdAndWarnLimit(String guildId, Integer warnLimit);
}
