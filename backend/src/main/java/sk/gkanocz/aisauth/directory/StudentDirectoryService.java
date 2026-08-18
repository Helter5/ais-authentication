package sk.gkanocz.aisauth.directory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface StudentDirectoryService {

    Optional<StudentRecord> findByAisId(String aisId);

    /**
     * One LDAP round trip for many AIS IDs (a single OR filter) instead of one per ID - callers with
     * a bulk lookup (the wipe job) would otherwise sit through the LDAP throttle's 1 req/sec once per
     * user. Missing entries are simply absent from the returned map, same "not found" meaning as
     * findByAisId's empty Optional.
     */
    Map<String, StudentRecord> findByAisIds(List<String> aisIds);
}
