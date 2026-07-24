package sk.gkanocz.aisauth.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import sk.gkanocz.aisauth.TestcontainersConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class AdminSettingRepositoryTest {

    @Autowired
    private AdminSettingRepository adminSettingRepository;

    @Test
    void findByKeyStartingWithOnlyReturnsMatchingKeysNotSubstringMatchesElsewhere() {
        adminSettingRepository.save(new AdminSetting("cmd_perms_guild-1_warn", "{}"));
        adminSettingRepository.save(new AdminSetting("cmd_perms_guild-1_verify", "{}"));
        adminSettingRepository.save(new AdminSetting("cmd_perms_guild-2_warn", "{}"));
        adminSettingRepository.save(new AdminSetting("cmd_states_guild-1", "{}"));

        List<AdminSetting> result = adminSettingRepository.findByKeyStartingWith("cmd_perms_guild-1_");

        assertThat(result).extracting(AdminSetting::getKey)
                .containsExactlyInAnyOrder("cmd_perms_guild-1_warn", "cmd_perms_guild-1_verify");
    }

    @Test
    void findByKeyStartingWithReturnsEmptyWhenNoneMatch() {
        assertThat(adminSettingRepository.findByKeyStartingWith("no_such_prefix_")).isEmpty();
    }
}
