package sk.gkanocz.aisauth.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuildSettingsRepository extends JpaRepository<GuildSettings, String> {

    List<GuildSettings> findByTicketRetentionEnabledTrue();
}
