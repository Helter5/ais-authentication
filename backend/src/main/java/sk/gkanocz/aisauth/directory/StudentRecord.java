package sk.gkanocz.aisauth.directory;

import java.util.List;

public record StudentRecord(
        String uisId,
        String mail,
        List<String> hosts,
        List<String> accountStatuses,
        String givenName,
        String surname,
        String displayName,
        String cn) {

    public boolean hasAccountStatus(String requiredStatus) {
        return accountStatuses.stream().anyMatch(status -> status.contains(requiredStatus));
    }

    public boolean belongsToAnyFaculty(List<String> allowedFaculties) {
        return hosts.stream().anyMatch(host ->
                allowedFaculties.stream().anyMatch(host::contains));
    }
}
