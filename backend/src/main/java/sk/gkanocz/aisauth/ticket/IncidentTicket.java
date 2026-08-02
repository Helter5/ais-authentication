package sk.gkanocz.aisauth.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "incident_tickets")
public class IncidentTicket {

    @Id
    @Column(name = "channel_id", length = 32)
    private String channelId;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "closed_by", length = 32)
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String transcript;

    @Column(name = "controls_message_id", length = 32)
    private String controlsMessageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IncidentTicket() {
        // JPA
    }

    public IncidentTicket(String channelId, String guildId, String userId) {
        this.channelId = channelId;
        this.guildId = guildId;
        this.userId = userId;
        this.status = "open";
        this.createdAt = LocalDateTime.now();
    }

    public boolean isOpen() {
        return "open".equals(status);
    }

    public void reopen() {
        this.status = "open";
        this.closedBy = null;
        this.closedAt = null;
        this.transcript = null;
    }

    public void close(String closedBy, String transcript) {
        this.status = "closed";
        this.closedBy = closedBy;
        this.closedAt = LocalDateTime.now();
        this.transcript = transcript;
    }

    public void setControlsMessageId(String controlsMessageId) {
        this.controlsMessageId = controlsMessageId;
    }
}
