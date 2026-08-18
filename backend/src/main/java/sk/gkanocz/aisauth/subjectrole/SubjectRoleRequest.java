package sk.gkanocz.aisauth.subjectrole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "subject_role_requests")
public class SubjectRoleRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "discord_id", nullable = false, length = 32)
    private String discordId;

    /** Discord role ID (picked via a ROLE-type slash command option, never typed text). */
    @Column(name = "subject_code", nullable = false, length = 64)
    private String subjectCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubjectRoleRequestStatus status;

    @Column(name = "decided_by", length = 32)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SubjectRoleRequest() {
        // JPA
    }

    public SubjectRoleRequest(String guildId, String discordId, String subjectCode, SubjectRoleRequestStatus status) {
        this.guildId = guildId;
        this.discordId = discordId;
        this.subjectCode = subjectCode;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public void decide(SubjectRoleRequestStatus status, String decidedBy) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
    }
}
