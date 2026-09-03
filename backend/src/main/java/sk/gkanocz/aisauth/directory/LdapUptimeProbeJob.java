package sk.gkanocz.aisauth.directory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Periodic, traffic-independent LDAP reachability check - feeds the admin dashboard's LDAP
 * connection uptime panel. Runs on its own schedule rather than piggybacking on real
 * {@link LdapStudentDirectoryService} calls so the uptime graph stays continuous even during
 * hours with no /verify activity at all, mirroring what the VPN sidecar's own Docker healthcheck
 * (infra/vpn/healthcheck.sh) already does at the container level, just recorded here so it's
 * visible in the dashboard over time.
 *
 * Deliberately bypasses {@link LdapRequestThrottle}: once every 60s is negligible load on the
 * real university LDAP server (the throttle exists to protect against concurrent /verify bursts,
 * not against a single slow heartbeat), and going through it would make the probe wait behind
 * real user traffic instead of sampling on a fixed cadence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LdapUptimeProbeJob {

    private final LdapTemplate ldapTemplate;
    private final LdapConnectionSampleRepository ldapConnectionSampleRepository;

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    void probe() {
        long start = System.nanoTime();
        boolean up = true;
        String error = null;
        try {
            ldapTemplate.lookup("");
        } catch (Exception e) {
            log.warn("LDAP uptime probe failed: {}", e.getMessage());
            up = false;
            error = e.getClass().getSimpleName();
        }
        try {
            ldapConnectionSampleRepository.save(new LdapConnectionSample(up, elapsedMs(start), error));
        } catch (RuntimeException e) {
            // Postgres unreachable - skip this sample rather than letting it bubble out as an
            // "Unexpected error occurred in scheduled task" ERROR every 60s during an outage.
            log.warn("Could not record LDAP uptime sample - database unavailable");
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
