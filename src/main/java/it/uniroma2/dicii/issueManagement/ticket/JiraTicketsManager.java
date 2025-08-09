package it.uniroma2.dicii.issueManagement.ticket;

import it.uniroma2.dicii.issueManagement.model.*;
import it.uniroma2.dicii.issueManagement.utils.JSONUtils;
import it.uniroma2.dicii.issueManagement.version.VersionsManager;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class JiraTicketsManager implements TicketsManager {

    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").appendOffset("+HHMM", "Z").toFormatter();
    private static final int PAGE_SIZE = 100;

    private final String projectName;
    private final String baseUrl;
    private final JSONUtils jsonUtils;

    @Getter
    private final List<Ticket> tickets;

    @Getter
    private final VersionsManager versionsManager;

    public JiraTicketsManager(VersionsManager versionsManager) {
        this.projectName = PropertiesManager.getInstance().getProperty("project.name");
        this.baseUrl = PropertiesManager.getInstance().getProperty("project.jira.baseUrl");
        this.jsonUtils = new JSONUtils();

        this.versionsManager = versionsManager;

        this.tickets = new ArrayList<>();
    }

    @Override
    public void clear() {
        this.tickets.clear();
    }

    @Override
    public void retrieveTickets() {
        this.retrieveTickets(new TicketFilter());
    }

    /**
     * Retrieves all tickets corresponding to the filter
     *
     * @param ticketFilter the ticket's filter
     */
    @Override
    public void retrieveTickets(TicketFilter ticketFilter) {
        int i = 0, j, total = 1;
        String baseUrl = buildUrlFromFilter(ticketFilter);
        String url;
        // Get JSON API for closed bugs w/ AV in the project
        log.info("Retrieving tickets");
        do {
            //Only gets a max of 100 at a time, so must do this multiple times if bugs > 100
            url = String.format(baseUrl, i, PAGE_SIZE);
            JSONObject json;
            try {
                json = jsonUtils.readJsonFromUrl(url);
            } catch (IOException e) {
                log.error("Unable to retrieve tickets IDs: {}", e.getMessage());
                return;
            }

            // Retrieves the "issues" array
            JSONArray issues = json.getJSONArray("issues");

            // Set the total number of issues found
            if (json.getInt("total") != total) total = json.getInt("total");

            // For each retrieved issue, adds a ticket to the list
            for (j = 0; j < issues.length(); j++) {
                // Iterate through each ticket
                JSONObject ticketJson = issues.getJSONObject(j);
                tickets.add(getTicketFromJson(ticketJson));
            }
            i += j;
        } while (i < total);

        log.info("Successfully retrieved {} ticket out of {} issues", tickets.size(), total);
    }

    @Override
    public void removeTicketsWithNoCommits() {
        List<Ticket> ticketsToRemove = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (ticket.getAssociatedCommits() == null || ticket.getAssociatedCommits().isEmpty()) {
                ticketsToRemove.add(ticket);
                log.warn("No commits associated with ticket {}. Removing...", ticket.getKey());
            }
        }
        tickets.removeAll(ticketsToRemove);
        log.warn("Successfully removed {} tickets with no associated commits", ticketsToRemove.size());
    }

    @Override
    public void setFixVersionToTickets() {
        tickets.stream().filter(t -> t.getFixed() == null).forEach(ticket -> {
            if (ticket.getAssociatedCommits() != null && !ticket.getAssociatedCommits().isEmpty()) {

                // Gets the last commit date and retrieves the first version released after
                // that date: this is then set as the fix version
                // Not checking for NullPointerException because of the control on the ticket's associated commits
                LocalDate lastCommitDate = ticket.getLastCommit().getCommitDate();
                Version fixVersion = versionsManager.getFirstVersionAfterDate(lastCommitDate);
                if (fixVersion != null) ticket.setFixed(fixVersion);
                else log.warn("No version exists after the last commit date ({}) for ticket {}.", lastCommitDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), ticket.getKey());
            } else log.warn("Ticket {} has no associated commits", ticket.getKey());
        });
    }

    /**
     * Parses a JSON to recover the ticket fields
     *
     * @param ticketJson the JSON of the ticket
     * @return a ticket object
     */
    private Ticket getTicketFromJson(JSONObject ticketJson) {
        JSONObject fields = ticketJson.getJSONObject("fields");
        LocalDate issuedDate = LocalDateTime.parse(fields.getString("created"), formatter).toLocalDate();
        LocalDate closedDate;
        if (fields.get("resolutiondate") != null && !fields.get("resolutiondate").toString().equals("null"))
            closedDate = LocalDateTime.parse(fields.getString("resolutiondate"), formatter).toLocalDate();
        else closedDate = LocalDateTime.parse(fields.getString("updated"), formatter).toLocalDate();
        TicketType issueType = TicketType.from(fields.getJSONObject("issuetype").getString("name"));
        TicketStatus status = TicketStatus.fromString(fields.getJSONObject("status").getString("name"));
        String assignee = "";
        if (fields.get("assignee") != null && !fields.get("assignee").toString().equals("null"))
            assignee = fields.getJSONObject("assignee").getString("name");
        ResolutionType resolutionType = null;
        if (fields.get("resolution") != null && !fields.get("resolution").toString().equals("null"))
            resolutionType = ResolutionType.fromResolution(fields.getJSONObject("resolution").getString("name"));
        Ticket ticket = new Ticket(ticketJson.getString("key"), issuedDate, closedDate, ticketJson.getString("id"), issueType, status, assignee);
        ticket.setResolution(resolutionType);

        JSONArray affectedVersionsJSONArray = fields.getJSONArray("versions");
        List<Version> affectedVersions = new ArrayList<>();
        if (affectedVersionsJSONArray != null && !affectedVersionsJSONArray.isEmpty()) {
            JSONObject versionJson;
            Version version;
            for (int i = 0; i < affectedVersionsJSONArray.length(); i++) {
                versionJson = affectedVersionsJSONArray.getJSONObject(i);
                version = versionsManager.getVersionByName(versionJson.getString("name"));
                if (version != null) affectedVersions.add(version);
                else log.warn("No versions found with name {}.", versionJson.getString("name"));
            }
        }
        affectedVersions.sort(Comparator.comparing(Version::getName));
        ticket.setAffectedVersions(affectedVersions);

        if (ticket.getAffectedVersions() != null && !ticket.getAffectedVersions().isEmpty()) {
            ticket.setInjected(ticket.getAffectedVersions().get(0));
        }

        Version openingVersion = versionsManager.getFirstVersionAfterDate(issuedDate);
        if (openingVersion != null) ticket.setOpening(openingVersion);
        else
            log.warn("No version exists after ticket {} opening date ({}).", ticket.getKey(), issuedDate.format(formatter));

        Version fixVersion = getFixVersionFromTicketJson(ticketJson);
        ticket.setFixed(fixVersion);
        if (fixVersion == null)
            log.warn("Warning: the following {} ticket was found with no fix versions. It will later be inferred from the associated commits.", ticket.getKey());

        return ticket;
    }

    /**
     * Retrieves the fix version from a ticket JSON
     *
     * @param ticketJson the JSON of the ticket to retrieve the fix version from
     * @return the fix version of the ticket, or null if the ticket has no fix version or if the fix version is not available in the project
     */
    private Version getFixVersionFromTicketJson(JSONObject ticketJson) {
        JSONArray fixVersionsArray = ticketJson.getJSONObject("fields").getJSONArray("fixVersions");

        if (fixVersionsArray == null || fixVersionsArray.isEmpty()) {
            return null;
        }

        List<Version> ticketFixVersions = new ArrayList<>();
        Version fixVersion = null;

        for (int i = 0; i < fixVersionsArray.length(); i++) {
            JSONObject fixVersionObj = fixVersionsArray.getJSONObject(i);
            String versionId = fixVersionObj.getString("name");

            // Using `new ArrayList<>` to create a mutable list
            Version foundVersion = versionsManager.getVersionByName(versionId);
            if (foundVersion != null) ticketFixVersions.add(foundVersion);
            else log.warn("No version found for id {}", versionId);
        }

        // Takes the most recent fix version in the array of fix versions
        if (!ticketFixVersions.isEmpty()) {
            if (ticketFixVersions.size() > 1) ticketFixVersions.sort(Comparator.comparing(Version::getReleaseDate));
            fixVersion = ticketFixVersions.get(ticketFixVersions.size() - 1);
        }

        return fixVersion;
    }


    /**
     * Builds a URL to query the Jira REST API according to some filters
     *
     * @param ticketFilter the filter with fields
     * @return the URL with filters set
     */
    private String buildUrlFromFilter(TicketFilter ticketFilter) {
        StringBuilder url = new StringBuilder(baseUrl + "/search?jql=project=\"" + projectName + "\"");

        if (ticketFilter.getStatuses() != null && !ticketFilter.getStatuses().isEmpty()) {
            url.append("AND(");
            boolean first = true;
            for (TicketStatus status : ticketFilter.getStatuses()) {
                if (!first) url.append("OR");
                url.append("\"status\"=\"").append(status.getStatus()).append("\"");
                first = false;
            }
            url.append(")");
        }

        if (ticketFilter.getTypes() != null && !ticketFilter.getTypes().isEmpty()) {
            url.append("AND(");
            boolean first = true;
            for (TicketType type : ticketFilter.getTypes()) {
                if (!first) url.append("OR");
                url.append("\"issueType\"=\"").append(type).append("\"");
                first = false;
            }
            url.append(")");
        }

        if (ticketFilter.getResolutions() != null && !ticketFilter.getResolutions().isEmpty()) {
            url.append("AND(");
            boolean first = true;
            for (ResolutionType type : ticketFilter.getResolutions()) {
                if (!first) url.append("OR");
                url.append("\"resolution\"=\"").append(type).append("\"");
                first = false;
            }
            url.append(")");
        }

        url.append("&startAt=%d&maxResults=%d");

        return url.toString();
    }

}