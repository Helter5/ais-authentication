package sk.gkanocz.aisauth.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "sessions")
public class AdminSession {

    @Id
    @Column(length = 36)
    private String jti;

    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminSession() {
        // JPA
    }

    public AdminSession(String jti, String userId, LocalDateTime expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }
}
