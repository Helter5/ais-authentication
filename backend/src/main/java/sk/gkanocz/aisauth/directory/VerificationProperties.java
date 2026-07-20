package sk.gkanocz.aisauth.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties (
    List<String> allowedFaculties,
    String requiredAccountStatus) {
}
