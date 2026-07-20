package sk.gkanocz.aisauth.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {

    boolean existsByJtiAndExpiresAtAfter(String jti, LocalDateTime now);

    void deleteByJti(String jti);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
