package sk.gkanocz.aisauth.directory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapStudentDirectoryServiceTest {

    @Mock
    private LdapTemplate ldapTemplate;
    @Mock
    private LdapRequestThrottle ldapRequestThrottle;

    private LdapStudentDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new LdapStudentDirectoryService(ldapTemplate, ldapRequestThrottle);
    }

    @Test
    void findByAisIdAwaitsThrottleTurnBeforeSearching() {
        when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class))).thenReturn(List.of());

        service.findByAisId("12345");

        verify(ldapRequestThrottle).awaitTurn();
    }

    @Test
    void findByAisIdReturnsEmptyWhenNoResults() {
        when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class))).thenReturn(List.of());

        Optional<StudentRecord> result = service.findByAisId("12345");

        assertThat(result).isEmpty();
    }

    @Test
    void findByAisIdReturnsFirstMatch() {
        StudentRecord record = new StudentRecord("12345", "a@b.sk", List.of(), List.of(), "A", "B", "A B", "cn");
        when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class))).thenReturn(List.of(record));

        Optional<StudentRecord> result = service.findByAisId("12345");

        assertThat(result).contains(record);
    }

    @SuppressWarnings("unchecked")
    private AttributesMapper<StudentRecord> capturedMapper() {
        when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class))).thenReturn(List.of());
        ArgumentCaptor<AttributesMapper<StudentRecord>> captor = ArgumentCaptor.forClass(AttributesMapper.class);
        service.findByAisId("12345");
        verify(ldapTemplate).search(any(LdapQuery.class), captor.capture());
        return captor.getValue();
    }

    private Attribute singleValueAttribute(Object value) {
        Attribute attribute = mock(Attribute.class);
        try {
            when(attribute.get()).thenReturn(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return attribute;
    }

    @SuppressWarnings("unchecked")
    private Attribute multiValueAttribute(String... values) throws Exception {
        Attribute attribute = mock(Attribute.class);
        NamingEnumeration<Object> enumeration = mock(NamingEnumeration.class);
        Enumeration<String> delegate = Collections.enumeration(List.of(values));
        when(enumeration.hasMore()).thenAnswer(inv -> delegate.hasMoreElements());
        when(enumeration.next()).thenAnswer(inv -> delegate.nextElement());
        when(attribute.getAll()).thenAnswer(inv -> enumeration);
        return attribute;
    }

    @Test
    void mapperReadsSingleAndMultiValuedAttributes() throws Exception {
        AttributesMapper<StudentRecord> mapper = capturedMapper();
        Attribute uisId = singleValueAttribute("12345");
        Attribute mail = singleValueAttribute("student@stuba.sk");
        Attribute host = multiValueAttribute("FEI", "FIIT");
        Attribute accountStatus = multiValueAttribute("ACTIVE");
        Attribute givenName = singleValueAttribute("Jane");
        Attribute sn = singleValueAttribute("Doe");
        Attribute displayName = singleValueAttribute("Jane Doe");
        Attribute cn = singleValueAttribute("jdoe");

        Attributes attributes = mock(Attributes.class);
        when(attributes.get("uisId")).thenReturn(uisId);
        when(attributes.get("mail")).thenReturn(mail);
        when(attributes.get("host")).thenReturn(host);
        when(attributes.get("accountStatus")).thenReturn(accountStatus);
        when(attributes.get("givenName")).thenReturn(givenName);
        when(attributes.get("sn")).thenReturn(sn);
        when(attributes.get("displayName")).thenReturn(displayName);
        when(attributes.get("cn")).thenReturn(cn);

        StudentRecord record = mapper.mapFromAttributes(attributes);

        assertThat(record.uisId()).isEqualTo("12345");
        assertThat(record.mail()).isEqualTo("student@stuba.sk");
        assertThat(record.hosts()).containsExactly("FEI", "FIIT");
        assertThat(record.accountStatuses()).containsExactly("ACTIVE");
        assertThat(record.givenName()).isEqualTo("Jane");
        assertThat(record.surname()).isEqualTo("Doe");
        assertThat(record.displayName()).isEqualTo("Jane Doe");
        assertThat(record.cn()).isEqualTo("jdoe");
    }

    @Test
    void mapperTreatsMissingAttributesAsNullOrEmpty() throws Exception {
        AttributesMapper<StudentRecord> mapper = capturedMapper();
        Attributes attributes = mock(Attributes.class);

        StudentRecord record = mapper.mapFromAttributes(attributes);

        assertThat(record.uisId()).isNull();
        assertThat(record.mail()).isNull();
        assertThat(record.hosts()).isEmpty();
        assertThat(record.accountStatuses()).isEmpty();
    }
}
