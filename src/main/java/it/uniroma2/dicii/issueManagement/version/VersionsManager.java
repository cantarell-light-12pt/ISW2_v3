package it.uniroma2.dicii.issueManagement.version;

import it.uniroma2.dicii.issueManagement.exceptions.VersionsException;
import it.uniroma2.dicii.issueManagement.model.Version;

import java.time.LocalDate;

public interface VersionsManager {

    /**
     * Retrieves all versions info
     */
    void getVersionsInfo() throws VersionsException;

    /**
     * List all versions
     */
    void listVersions();

    /**
     * Retrieves the version corresponding to the given name (e.g., 4.0.0)
     *
     * @param versionName the name of the version to be retrieved
     * @return the version corresponding to the given name, or null if no version is found
     */
    Version getVersionByName(String versionName);

    /**
     * Retrieves the first version released after the given date
     *
     * @param date the date after which the first released version is retrieved
     * @return the first released version released after the given date, or null if no version is released after the given date
     */
    Version getFirstVersionAfterDate(LocalDate date);

}
