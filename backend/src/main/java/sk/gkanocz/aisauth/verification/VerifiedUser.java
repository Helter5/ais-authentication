package sk.gkanocz.aisauth.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import java.time.LocalDateTime;
import jakarta.persistence.GenerationType;

@Getter
@Entity
@Table(name = "verified_users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"discord_id", "guild_id"}),
    @UniqueConstraint(columnNames = {"ais_id", "guild_id"})
})
public class VerifiedUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ais_id", nullable = false, length = 32)
    private String aisId;

    @Column(name = "discord_id", nullable = false, length = 32)
    private String discordId;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    protected VerifiedUser() {
        // JPA
    }

    public VerifiedUser(String aisId, String discordId, String guildId, String email) {
        this.aisId = aisId;
        this.discordId = discordId;
        this.guildId = guildId;
        this.email = email;
        this.verifiedAt = LocalDateTime.now();
    }
}
