package it.uniroma2.dicii.issueManagement.version;

import it.uniroma2.dicii.issueManagement.exceptions.VersionsException;
import it.uniroma2.dicii.issueManagement.model.Version;
import it.uniroma2.dicii.issueManagement.utils.JSONUtils;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
public class JiraVersionsManager implements VersionsManager {

    private final String url;

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

        this.versions.clear();
        // Takes the "versions" field from the JSON
        int versionsNumber = versions.length();
        String name, id;
        boolean released = false;
        for (int i = 0; i < versionsNumber; i++) {
            name = id = "";
            if (versions.getJSONObject(i).has("releaseDate")) {
                if (versions.getJSONObject(i).has("name")) name = versions.getJSONObject(i).get("name").toString();
                if (versions.getJSONObject(i).has("id")) id = versions.getJSONObject(i).get("id").toString();
                if (versions.getJSONObject(i).has("released"))
                    released = versions.getJSONObject(i).getBoolean("released");
                this.versions.add(new Version(id, name, LocalDate.parse(versions.getJSONObject(i).getString("releaseDate")), released));
            } else {
                log.warn("Version {} has no release date", id);
            }
        }

        // Order releases by date, from oldest to newest
        this.versions.sort(Comparator.comparing(Version::getReleaseDate));

        log.info("Successfully retrieved {} versions out of {} releases", this.versions.size(), versionsNumber);
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
