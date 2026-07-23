package sk.gkanocz.aisauth.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminSettingRepository extends JpaRepository<AdminSetting, String> {

    List<AdminSetting> findByKeyStartingWith(String prefix);
}
