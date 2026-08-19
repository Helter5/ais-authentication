package sk.gkanocz.aisauth.directory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
class LdapStudentDirectoryService implements StudentDirectoryService {

    private static final int MAX_ATTEMPTS = 4; // 1 initial attempt + 3 retries
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);

    private final LdapTemplate ldapTemplate;
    private final LdapRequestThrottle ldapRequestThrottle;

    @Override
    public Optional<StudentRecord> findByAisId(String aisId) {
        ldapRequestThrottle.awaitTurn();
        List<StudentRecord> results = searchWithRetry(LdapQueryBuilder.query().where("uisId").is(aisId));

        return results.stream().findFirst();
    }

    @Override
    public Map<String, StudentRecord> findByAisIds(List<String> aisIds) {
        if (aisIds.isEmpty()) {
            return Map.of();
        }
        ldapRequestThrottle.awaitTurn();
        ContainerCriteria criteria = LdapQueryBuilder.query().where("uisId").is(aisIds.get(0));
        for (int i = 1; i < aisIds.size(); i++) {
            criteria = criteria.or("uisId").is(aisIds.get(i));
        }
        List<StudentRecord> results = searchWithRetry(criteria);
        return results.stream().collect(Collectors.toMap(StudentRecord::uisId, Function.identity(), (a, b) -> a));
    }

    /**
     * Up to {@value #MAX_ATTEMPTS} - 1 retries, 2s apart, on CommunicationException - the
     * university VPN tunnel restarts every ~2 minutes (STU-pushed ping-restart policy over an
     * unreliable network path), and a search that lands mid-restart fails with a read timeout
     * even though the tunnel is healthy again a couple seconds later. Retrying turns that into a
     * slightly slower search instead of a user-visible /verify failure. This all happens after
     * VerifyRateLimiter has already recorded the attempt, so retries here don't cost the user
     * extra /verify attempts.
     */
    private List<StudentRecord> searchWithRetry(LdapQuery query) {
        CommunicationException lastFailure;
        int attempt = 1;
        while (true) {
            try {
                return ldapTemplate.search(query, this::mapAttributes);
            } catch (CommunicationException e) {
                lastFailure = e;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
                log.warn("LDAP search failed (attempt {}/{}), retrying in {} - likely mid VPN ping-restart: {}",
                        attempt, MAX_ATTEMPTS, RETRY_BACKOFF, e.getMessage());
                try {
                    Thread.sleep(RETRY_BACKOFF.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                attempt++;
            }
        }
        throw lastFailure;
    }

    private StudentRecord mapAttributes(Attributes attributes) throws NamingException {
        return new StudentRecord(
                getSingleValue(attributes, "uisId"),
                getSingleValue(attributes, "mail"),
                getMultiValue(attributes, "host"),
                getMultiValue(attributes, "accountStatus"),
                getSingleValue(attributes, "givenName"),
                getSingleValue(attributes, "sn"),
                getSingleValue(attributes, "displayName"),
                getSingleValue(attributes, "cn"));
    }

    private String getSingleValue(Attributes attributes, String id) throws NamingException {
        Attribute attribute = attributes.get(id);
        return attribute == null ? null : (String) attribute.get();
    }

    private List<String> getMultiValue(Attributes attributes, String id) throws NamingException {
        Attribute attribute = attributes.get(id);
        if (attribute == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        NamingEnumeration<?> enumeration = attribute.getAll();
        while (enumeration.hasMore()) {
            values.add((String) enumeration.next());
        }
        return values;
    }
}
