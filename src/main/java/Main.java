import it.uniroma2.dicii.issueManagement.exceptions.VersionsException;
import it.uniroma2.dicii.issueManagement.model.*;
import it.uniroma2.dicii.issueManagement.ticket.JiraTicketsManager;
import it.uniroma2.dicii.issueManagement.ticket.TicketsManager;
import it.uniroma2.dicii.issueManagement.version.JiraVersionsManager;
import it.uniroma2.dicii.issueManagement.version.VersionsManager;
import it.uniroma2.dicii.vcsManagement.commit.GitCommitManager;
import it.uniroma2.dicii.vcsManagement.exception.CommitException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class Main {

    private static boolean verbose = false;

    public static void main(String[] args) throws IOException {

        if (args.length > 0 && args[0].equals("-v")) verbose = true;

        log.info("Starting process...");

        try {
            // Retrieves versions from Jira
            VersionsManager versionsManager = new JiraVersionsManager();
            versionsManager.getVersionsInfo();

            if (verbose) versionsManager.listVersions();

            // Retrieves tickets from Jira
            TicketsManager ticketsManager = new JiraTicketsManager(versionsManager);

            TicketFilter filter = new TicketFilter();
            filter.setStatuses(List.of(TicketStatus.CLOSED, TicketStatus.RESOLVED));
            filter.setTypes(List.of(TicketType.BUG));
            filter.setResolutions(List.of(ResolutionType.FIXED));

            ticketsManager.retrieveTickets(filter);

            // Get Git commits and associates them with tickets
            GitCommitManager gitCommitManager = new GitCommitManager(versionsManager, ticketsManager);

            gitCommitManager.getCommitsWithTickets();

            ticketsManager.removeTicketsWithNoCommits();

            // After gaining all ticket commits, infers the fix version from these (when not available)
            ticketsManager.setFixVersionToTickets();

            gitCommitManager.retrieveVersionsCommits();

            log.info("Successfully retrieved commits with tickets");
            log.info("Starting collectin metrics");

            log.info("Process terminated");

        } catch (VersionsException e) {
            log.error("Error retrieving versions: {}", e.getMessage(), e);
        } catch (CommitException e) {
            log.error("Error retrieving commits: {}", e.getMessage(), e);
        }
    }
}
