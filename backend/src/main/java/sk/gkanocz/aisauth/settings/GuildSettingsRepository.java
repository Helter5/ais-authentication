package sk.gkanocz.aisauth.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildSettingsRepository extends JpaRepository<GuildSettings, String> {
}
