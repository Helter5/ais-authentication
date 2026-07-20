package sk.gkanocz.aisauth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class VerificationEmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    void send(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Discord - Overovací kód");
        message.setText(buildBody(code));
        mailSender.send(message);
    }

    private String buildBody(String code) {
        return """
                Ahoj.

                Tvoj overovací kód je: %s

                Na discorde napíš ručne príkaz:
                /code %s

                Kód platí 15 minút.
                """.formatted(code, code);
    }
}
