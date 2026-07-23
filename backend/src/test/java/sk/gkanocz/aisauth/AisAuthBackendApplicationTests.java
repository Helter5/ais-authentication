package sk.gkanocz.aisauth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * contextLoads() boots the full application against a real (Testcontainers) Postgres, which
 * means Hibernate's ddl-auto=validate checks every entity's mapping against the actual Flyway-
 * migrated schema here - this is what would have caught the autodelete_configs JSONB/TEXT
 * mismatch immediately instead of at deploy time.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AisAuthBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
