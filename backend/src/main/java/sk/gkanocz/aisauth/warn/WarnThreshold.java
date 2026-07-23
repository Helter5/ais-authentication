package sk.gkanocz.aisauth.warn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(name = "warn_thresholds", uniqueConstraints = @UniqueConstraint(columnNames = {"guild_id", "warn_limit"}))
public class WarnThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "warn_limit", nullable = false)
    private Integer warnLimit;

    @Column(nullable = false, length = 16)
    private String action;

    protected WarnThreshold() {
        // JPA
    }

    public WarnThreshold(String guildId, Integer warnLimit, String action) {
        this.guildId = guildId;
        this.warnLimit = warnLimit;
        this.action = action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
