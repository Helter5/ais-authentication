package sk.gkanocz.aisauth.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.directory.VerificationProperties;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationFacadeTest {

    @Mock
    private VerificationService verificationService;
    @Mock
    private VerificationEmailSender verificationEmailSender;

    @Test
    void sendsTheVerificationEmailWhenNotInTestingMode() {
        VerificationProperties props = new VerificationProperties(List.of(), "ACTIVE", false);
        VerificationFacade facade = new VerificationFacade(verificationService, verificationEmailSender, props);
        VerificationCode code = new VerificationCode(
                "user-1", "guild-1", "123456", "student@stuba.sk", "ais-1", LocalDateTime.now().plusMinutes(15));
        when(verificationService.initiateVerification("user-1", "guild-1", "ais-1")).thenReturn(code);

        VerificationCode result = facade.initiateAndNotify("user-1", "guild-1", "ais-1");

        assertThat(result).isEqualTo(code);
        verify(verificationEmailSender).send("student@stuba.sk", "123456");
    }

    @Test
    void skipsTheVerificationEmailInTestingMode() {
        VerificationProperties props = new VerificationProperties(List.of(), "ACTIVE", true);
        VerificationFacade facade = new VerificationFacade(verificationService, verificationEmailSender, props);
        VerificationCode code = new VerificationCode(
                "user-1", "guild-1", "123456", "student@stuba.sk", "ais-1", LocalDateTime.now().plusMinutes(15));
        when(verificationService.initiateVerification("user-1", "guild-1", "ais-1")).thenReturn(code);

        VerificationCode result = facade.initiateAndNotify("user-1", "guild-1", "ais-1");

        assertThat(result).isEqualTo(code);
        verify(verificationEmailSender, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
