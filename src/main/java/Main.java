import it.uniroma2.dicii.issueManagement.exceptions.VersionsException;
import it.uniroma2.dicii.issueManagement.model.*;
import it.uniroma2.dicii.issueManagement.proportion.ProportionApplier;
import it.uniroma2.dicii.issueManagement.proportion.ProportionApplierImpl;
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

            // Gets commits from GitHub and associates them with tickets
            GitCommitManager gitCommitManager = new GitCommitManager(ticketsManager);
            gitCommitManager.linkCommitsToTickets();

            // Tickets having no associates commits are removed
            ticketsManager.removeTicketsWithNoCommits();

            // After gaining all ticket commits, when not available, infers the fix version from these
            ticketsManager.setFixVersionToTickets();

            log.info("Successfully retrieved commits with tickets");

            log.info("Applying proportion to tickets");
            ProportionApplier proportionApplier = new ProportionApplierImpl(versionsManager);
            proportionApplier.applyProportions(ticketsManager.getTickets());
            log.info("Successfully applied proportion to tickets");

            ticketsManager.getTickets().stream().filter(t -> t.getInjected() == null || t.getOpening() == null || t.getFixed() == null).forEach(
                    t -> log.warn("Ticket {}; \t iv: {}; \t op: {}; \t fx: {}", t.getKey(), t.getInjected(), t.getOpening(), t.getFixed())
            );

            /*
            if (.toList().isEmpty())
                log.info("All tickets have injected version, opening version, and fixed version");

             */

            log.info("Starting collectin metrics");

            log.info("Process terminated");

        } catch (VersionsException e) {
            log.error("Error retrieving versions: {}", e.getMessage(), e);
        } catch (CommitException e) {
            log.error("Error retrieving commits: {}", e.getMessage(), e);
        }
    }
}
