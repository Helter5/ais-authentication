package sk.gkanocz.aisauth.directory;

import java.util.Optional;

public interface StudentDirectoryService {

    Optional<StudentRecord> findByAisId(String aisId);
}
