package sk.gkanocz.aisauth.directory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LdapConnectionSampleRepository extends JpaRepository<LdapConnectionSample, Long> {

    List<LdapConnectionSample> findBySampledAtAfterOrderBySampledAtAsc(LocalDateTime after);

    Optional<LdapConnectionSample> findTopByOrderBySampledAtDesc();

    void deleteBySampledAtBefore(LocalDateTime before);
}
