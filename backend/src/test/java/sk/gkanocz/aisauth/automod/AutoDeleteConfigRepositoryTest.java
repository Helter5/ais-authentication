package sk.gkanocz.aisauth.automod;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import sk.gkanocz.aisauth.TestcontainersConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for the ignore_role_ids/ignore_user_ids JSONB columns: V9 originally
 * declared them TEXT while the entity expected JSON, which only surfaced at runtime once
 * Hibernate validated the schema against a real Postgres (see V10 migration).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class AutoDeleteConfigRepositoryTest {

    @Autowired
    private AutoDeleteConfigRepository autoDeleteConfigRepository;

    @Test
    void jsonRoleAndUserIdListsRoundTripThroughThePostgresJsonbColumns() {
        AutoDeleteConfig config = new AutoDeleteConfig(
                "guild-1", "chan-1", 60, "[\"role-a\",\"role-b\"]", "[\"user-a\"]",
                true, true, false, "channel", "msg", true, 5);

        Long id = autoDeleteConfigRepository.save(config).getId();
        autoDeleteConfigRepository.flush();
        AutoDeleteConfig reloaded = autoDeleteConfigRepository.findById(id).orElseThrow();

        // Postgres reformats jsonb on storage (adds spacing) - proof it's actually stored as
        // jsonb and not just passed through as opaque text, which is exactly what V10 fixed.
        assertThat(reloaded.getIgnoreRoleIds()).contains("role-a").contains("role-b");
        assertThat(reloaded.getIgnoreUserIds()).contains("user-a");
    }

    @Test
    void guildIdAndChannelIdCombinationIsUnique() {
        autoDeleteConfigRepository.save(new AutoDeleteConfig(
                "guild-2", "chan-dup", 60, "[]", "[]", true, true, false, "channel", "msg", true, 5));
        autoDeleteConfigRepository.flush();

        assertThatThrownBy(() -> {
            autoDeleteConfigRepository.save(new AutoDeleteConfig(
                    "guild-2", "chan-dup", 60, "[]", "[]", true, true, false, "channel", "msg", true, 5));
            autoDeleteConfigRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByGuildIdOrderByCreatedAtAscReturnsOnlyThatGuildsConfigs() {
        autoDeleteConfigRepository.save(new AutoDeleteConfig(
                "guild-3", "chan-a", 60, "[]", "[]", true, true, false, "channel", "msg", true, 5));
        autoDeleteConfigRepository.save(new AutoDeleteConfig(
                "guild-4", "chan-b", 60, "[]", "[]", true, true, false, "channel", "msg", true, 5));

        List<AutoDeleteConfig> result = autoDeleteConfigRepository.findByGuildIdOrderByCreatedAtAsc("guild-3");

        assertThat(result).extracting(AutoDeleteConfig::getChannelId).containsExactly("chan-a");
    }
}
