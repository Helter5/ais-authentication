package sk.gkanocz.aisauth.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerificationEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private VerificationEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new VerificationEmailSender(mailSender);
        ReflectionTestUtils.setField(sender, "fromAddress", "noreply@stuba.sk");
    }

    @Test
    void sendsAVerificationEmailWithTheCodeInSubjectAndBody() {
        sender.send("student@stuba.sk", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom()).isEqualTo("noreply@stuba.sk");
        assertThat(message.getTo()).containsExactly("student@stuba.sk");
        assertThat(message.getSubject()).isEqualTo("Discord - Overovací kód");
        assertThat(message.getText()).contains("123456").contains("/code 123456");
    }
}
