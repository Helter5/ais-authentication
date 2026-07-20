package sk.gkanocz.aisauth.warn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "warns")
public class Warn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "discord_id", nullable = false, length = 32)
    private String discordId;

    @Column(name = "moderator_id", nullable = false, length = 32)
    private String moderatorId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Warn() {
        // JPA
    }

    public Warn(String guildId, String discordId, String moderatorId, String reason) {
        this.guildId = guildId;
        this.discordId = discordId;
        this.moderatorId = moderatorId;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }
}
