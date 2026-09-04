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

                >>> PREČÍTAJ SI TOTO NAJPRV <<<

                Po overení si nezabudni nastaviť roly na Discorde:
                  * svoj odbor                  -> kanál #odbor-roles
                  * povinne voliteľné predmety  -> kanál #pv-predmety-roles
                  * záujmy (gaming, anime, motorsport...) -> kanál #zaujmy-roles

                Ak máš nejaký všeobecný problém, napíš nám na Discorde cez kanál #kontakt-ticket.

                ============================================
                     TVOJ OVEROVACÍ KÓD:   %s
                ============================================

                Na Discorde napíš ručne príkaz:
                    /code %s

                Kód platí 15 minút.
                """.formatted(code, code);
    }
}
