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

    private static final int MAX_ATTEMPTS = 2; // 1 initial attempt + 1 retry

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
     * One retry, immediately, on CommunicationException - measured outages against the university
     * LDAP path are a recurring ~60-65s dead / ~50s alive cycle, not a brief blip, and the
     * underlying connection recovers on its own without being torn down (confirmed with a plain
     * ldapsearch: it just sits there until traffic resumes, then succeeds). The 90s read timeout
     * (see application.yml) is sized to outlast the dead window on its own, so a single retry is
     * only a safety net for the rare longer outage - no backoff needed, since by the time the
     * first 90s attempt gives up, the dead window has almost certainly already ended. This all
     * happens after VerifyRateLimiter has already recorded the attempt, so a retry here doesn't
     * cost the user an extra /verify attempt.
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
                log.warn("LDAP search failed (attempt {}/{}), retrying immediately: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
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
