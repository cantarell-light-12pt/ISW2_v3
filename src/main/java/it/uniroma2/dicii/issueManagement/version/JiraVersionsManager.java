package it.uniroma2.dicii.issueManagement.version;

import it.uniroma2.dicii.issueManagement.exceptions.VersionsException;
import it.uniroma2.dicii.issueManagement.model.Version;
import it.uniroma2.dicii.issueManagement.utils.JSONUtils;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
public class JiraVersionsManager implements VersionsManager {

    private final String url;

    @Getter
    private final List<Version> versions;

    private final JSONUtils jsonUtils;

    public JiraVersionsManager() {
        String projectName = PropertiesManager.getInstance().getProperty("project.name").toUpperCase(Locale.ROOT);
        String baseUrl = PropertiesManager.getInstance().getProperty("project.jira.baseUrl");

        this.url = String.format("%s/project/%s/versions", baseUrl, projectName);

        this.versions = new ArrayList<>();
        this.jsonUtils = new JSONUtils();
    }

    /**
     * Retrieves all the releases for the project. Ignores releases with missing dates
     */
    @Override
    public void getVersionsInfo() throws VersionsException {
        JSONArray versions;
        try {
            versions = jsonUtils.readJsonArrayFromUrl(url);
        } catch (IOException e) {
            throw new VersionsException("Unable to retrieve versions from " + url, e);
        } catch (JSONException e) {
            throw new VersionsException("Unable to parse versions retrieved from " + url, e);
        }

        // If no error occurred, parses the versions from the JSON
        this.versions.clear();
        int versionsNumber = versions.length();
        String name, id;
        boolean released = false;
        boolean overdue = false;
        JSONObject jsonObject;
        for (int i = 0; i < versionsNumber; i++) {
            name = id = "";
            jsonObject = versions.getJSONObject(i);
            if (jsonObject.has("releaseDate")) {
                if (jsonObject.has("name"))
                    name = jsonObject.get("name").toString();
                if (jsonObject.has("id"))
                    id = jsonObject.get("id").toString();
                if (jsonObject.has("released"))
                    released = jsonObject.getBoolean("released");
                if (jsonObject.has("overdue"))
                    released = jsonObject.getBoolean("overdue");
                this.versions.add(new Version(id, name, LocalDate.parse(jsonObject.getString("releaseDate")), released, overdue));
            } else {
                log.warn("Version {} has no release date", id);
            }
        }

        // Order releases by name
        this.versions.sort(Version::compare);

        log.info("Successfully retrieved {} versions out of {} releases", this.versions.size(), versionsNumber);
    }

    @Override
    public void removeUnreleasedVersions() {
        List<Version> versionsToRemove = new ArrayList<>();
        for (Version version : this.versions) {
            if (!version.isReleased() && !version.isOverdue()) {
                versionsToRemove.add(version);
                log.warn("Version {} has not been released. Removing...", version.getName());
            }
        }
        this.versions.removeAll(versionsToRemove);
        log.warn("Successfully removed {} unreleased versions", versionsToRemove.size());
    }

    @Override
    public void listVersions() {
        for (Version version : this.versions) {
            log.info("Version {} released on date {}", version.getName(), version.getReleaseDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
    }

    @Override
    public Version getVersionByName(String versionName) {
        return this.versions.stream().filter(v -> v.getName().equals(versionName)).findFirst().orElse(null);
    }

    @Override
    public Version getFirstVersionAfterDate(LocalDate date) {
        return this.versions.stream().filter(v -> v.getReleaseDate().isAfter(date)).findFirst().orElse(null);
    }

}
