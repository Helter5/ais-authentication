package sk.gkanocz.aisauth.directory;

import lombok.RequiredArgsConstructor;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.ContainerCriteria;
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


@Service
@RequiredArgsConstructor
class LdapStudentDirectoryService implements StudentDirectoryService {

    private final LdapTemplate ldapTemplate;
    private final LdapRequestThrottle ldapRequestThrottle;

    @Override
    public Optional<StudentRecord> findByAisId(String aisId) {
        ldapRequestThrottle.awaitTurn();
        List<StudentRecord> results = ldapTemplate.search(
                LdapQueryBuilder.query().where("uisId").is(aisId),
                this::mapAttributes);

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
        List<StudentRecord> results = ldapTemplate.search(criteria, this::mapAttributes);
        return results.stream().collect(Collectors.toMap(StudentRecord::uisId, Function.identity(), (a, b) -> a));
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
