package sk.gkanocz.aisauth.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * One row per (login, eligible guild) pair - a deliberate simplification of the original
 * schema, which stored a single row per login with a JSON array of guild ids. Denormalizing
 * makes "show me access for guild X" a plain indexed lookup instead of a JSON-containment query.
 */
@Getter
@Entity
@Table(name = "access_logs")
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discord_id", nullable = false, length = 32)
    private String discordId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AccessLog() {
        // JPA
    }

    public AccessLog(String discordId, String username, String action, String guildId, String ip) {
        this.discordId = discordId;
        this.username = username;
        this.action = action;
        this.guildId = guildId;
        this.ip = ip;
        this.createdAt = LocalDateTime.now();
    }
}
