package sk.gkanocz.aisauth.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(List<String> superAdminIds) {
    
}
