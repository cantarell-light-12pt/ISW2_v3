package it.uniroma2.dicii;

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
public class Application {

    public void execute(boolean verbose) {
        log.info("Starting process...");

        try {
            VersionsManager versionsManager = new JiraVersionsManager();
            versionsManager.getVersionsInfo();

            if (verbose) versionsManager.listVersions();

            TicketsManager ticketsManager = new JiraTicketsManager(versionsManager);

            TicketFilter filter = buildTicketFilter();
            ticketsManager.retrieveTickets(filter);

            try (GitCommitManager gitCommitManager = new GitCommitManager(ticketsManager)) {
                retrieveCommits(gitCommitManager, ticketsManager);
                applyProportions(versionsManager, ticketsManager);
                logUnusableTickets(ticketsManager);
            }
        } catch (VersionsException e) {
            log.error("Error retrieving versions: {}", e.getMessage(), e);
        } catch (CommitException | IOException e) {
            log.error("Error retrieving commits: {}", e.getMessage(), e);
        } finally {
            log.info("Process terminated");
        }
    }

    private TicketFilter buildTicketFilter() {
        TicketFilter filter = new TicketFilter();
        filter.setStatuses(List.of(TicketStatus.CLOSED, TicketStatus.RESOLVED));
        filter.setTypes(List.of(TicketType.BUG));
        filter.setResolutions(List.of(ResolutionType.FIXED));
        return filter;
    }

    /**
     * Retrieves all commits associated with tickets
     *
     * @param gitCommitManager the git commit manager to retrieve commits from
     * @param ticketsManager the tickets manager to retrieve tickets from
     * @throws CommitException if an error occurs while retrieving commits
     * @throws IOException if an error occurs while retrieving the repository
     */
    private void retrieveCommits(GitCommitManager gitCommitManager, TicketsManager ticketsManager) throws CommitException, IOException {
        log.info("Retrieving commits associated with tickets");
        gitCommitManager.linkCommitsToTickets();
        ticketsManager.removeTicketsWithNoCommits();
        ticketsManager.setFixVersionToTickets();
        log.info("Successfully retrieved commits associated with tickets");
    }

    /**
     * Applies the (full) proportion to the tickets
     *
     * @param versionsManager the versions manager to retrieve versions from, used to retrieve the first version after a given date
     * @param ticketsManager the tickets manager to retrieve tickets from
     */
    private void applyProportions(VersionsManager versionsManager, TicketsManager ticketsManager) {
        log.info("Applying proportion to tickets");
        ProportionApplier proportionApplier = new ProportionApplierImpl(versionsManager);
        proportionApplier.applyProportions(ticketsManager.getTickets());
        log.info("Successfully applied proportion to tickets");
    }

    /**
     * Logs ticket with either no opening, injected or fixed version
     *
     * @param ticketsManager the tickets manager to retrieve tickets from
     */
    private void logUnusableTickets(TicketsManager ticketsManager) {
        ticketsManager.getTickets().stream()
                .filter(t -> t.getInjected() == null || t.getOpening() == null || t.getFixed() == null)
                .forEach(t -> log.debug("Ticket {}; \t iv: {}; \t op: {}; \t fx: {}",
                        t.getKey(), t.getInjected(), t.getOpening(), t.getFixed()));
    }
}