package sk.gkanocz.aisauth.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {

    @Mock
    private AdminSettingRepository adminSettingRepository;

    private final ObjectMapper objectMapper = new JsonMapper();
    private AdminSettingsService adminSettingsService;

    @BeforeEach
    void setUp() {
        // Real (un-proxied) reader over the mocked repo: no Spring cache proxy in a unit test, so it's
        // a straight pass-through and the findById(...) stubs below still drive every read.
        AdminSettingReader adminSettingReader = new AdminSettingReader(adminSettingRepository);
        adminSettingsService = new AdminSettingsService(adminSettingReader, adminSettingRepository, objectMapper);
    }

    @Test
    void getReturnsFallbackWhenKeyMissing() {
        when(adminSettingRepository.findById("missing_key")).thenReturn(Optional.empty());

        assertThat(adminSettingsService.get("missing_key", Boolean.class, false)).isFalse();
    }

    @Test
    void getDeserializesStoredValue() {
        when(adminSettingRepository.findById("maintenance_mode")).thenReturn(Optional.of(new AdminSetting("maintenance_mode", "true")));

        assertThat(adminSettingsService.get("maintenance_mode", Boolean.class, false)).isTrue();
    }

    @Test
    void getFallsBackOnCorruptJsonInsteadOfThrowing() {
        when(adminSettingRepository.findById("broken")).thenReturn(Optional.of(new AdminSetting("broken", "{not valid json")));

        assertThat(adminSettingsService.get("broken", Boolean.class, true)).isTrue();
    }

    @Test
    void getWithTypeReferenceDeserializesLists() {
        when(adminSettingRepository.findById("allowed_guild_ids"))
                .thenReturn(Optional.of(new AdminSetting("allowed_guild_ids", "[\"111\",\"222\"]")));

        List<String> result = adminSettingsService.get(
                "allowed_guild_ids", new TypeReference<List<String>>() { }, List.of());

        assertThat(result).containsExactly("111", "222");
    }

    @Test
    void setCreatesNewRowWhenKeyDoesNotExist() {
        when(adminSettingRepository.findById("new_key")).thenReturn(Optional.empty());

        adminSettingsService.set("new_key", true);

        verify(adminSettingRepository).save(any());
    }

    @Test
    void setUpdatesExistingRowInPlaceInsteadOfInserting() {
        AdminSetting existing = new AdminSetting("existing_key", "false");
        when(adminSettingRepository.findById("existing_key")).thenReturn(Optional.of(existing));

        adminSettingsService.set("existing_key", true);

        assertThat(existing.getValue()).isEqualTo("true");
        verify(adminSettingRepository, never()).save(any());
    }

    @Test
    void removeDeletesByKey() {
        adminSettingsService.remove("some_key");

        verify(adminSettingRepository).deleteById("some_key");
    }
}
