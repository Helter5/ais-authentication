package sk.gkanocz.aisauth.directory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * One row per periodic LDAP reachability probe ({@link LdapUptimeProbeJob}) - the raw data behind
 * the LDAP connection uptime panel on the admin dashboard. Deliberately just a bare success/latency
 * sample, not tied to any real /verify call, so the timeline stays continuous even during hours with
 * zero Discord traffic.
 */
@Getter
@Entity
@Table(name = "ldap_connection_samples")
public class LdapConnectionSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sampled_at", nullable = false)
    private LocalDateTime sampledAt;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_type", length = 64)
    private String errorType;

    protected LdapConnectionSample() {
        // JPA
    }

    public LdapConnectionSample(boolean success, Long latencyMs, String errorType) {
        this.sampledAt = LocalDateTime.now();
        this.success = success;
        this.latencyMs = latencyMs;
        this.errorType = errorType;
    }
}
