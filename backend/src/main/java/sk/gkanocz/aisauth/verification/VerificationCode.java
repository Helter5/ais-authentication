package sk.gkanocz.aisauth.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;


@Getter
@Entity
@Table(name = "verification_codes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"discord_id", "guild_id"})
})
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discord_id", nullable = false, length = 32)
    private String discordId;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false)
    private String email;

    @Column(name = "ais_id", nullable = false, length = 32)
    private String aisId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected VerificationCode() {
        // JPA
    }

    public VerificationCode(String discordId, String guildId, String code, String email, String aisId, LocalDateTime expiresAt) {
        this.discordId = discordId;
        this.guildId = guildId;
        this.code = code;
        this.email = email;
        this.aisId = aisId;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}