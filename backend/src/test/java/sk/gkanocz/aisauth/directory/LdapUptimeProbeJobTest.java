package sk.gkanocz.aisauth.directory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.LdapTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapUptimeProbeJobTest {

    @Mock
    private LdapTemplate ldapTemplate;
    @Mock
    private LdapConnectionSampleRepository ldapConnectionSampleRepository;

    private LdapUptimeProbeJob job;

    @BeforeEach
    void setUp() {
        job = new LdapUptimeProbeJob(ldapTemplate, ldapConnectionSampleRepository);
    }

    @Test
    void recordsSuccessfulSampleWhenLookupSucceeds() {
        when(ldapTemplate.lookup("")).thenReturn(new Object());

        job.probe();

        ArgumentCaptor<LdapConnectionSample> captor = ArgumentCaptor.forClass(LdapConnectionSample.class);
        verify(ldapConnectionSampleRepository).save(captor.capture());
        LdapConnectionSample saved = captor.getValue();
        assertThat(saved.isSuccess()).isTrue();
        assertThat(saved.getErrorType()).isNull();
        assertThat(saved.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void recordsFailedSampleWithErrorTypeWhenLookupThrows() {
        when(ldapTemplate.lookup("")).thenThrow(new CommunicationException(
                new javax.naming.CommunicationException("connection refused")));

        job.probe();

        ArgumentCaptor<LdapConnectionSample> captor = ArgumentCaptor.forClass(LdapConnectionSample.class);
        verify(ldapConnectionSampleRepository).save(captor.capture());
        LdapConnectionSample saved = captor.getValue();
        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getErrorType()).isEqualTo("CommunicationException");
    }
}
