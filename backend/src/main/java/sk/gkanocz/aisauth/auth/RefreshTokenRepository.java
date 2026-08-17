package sk.gkanocz.aisauth.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);

    void deleteByToken(String token);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
