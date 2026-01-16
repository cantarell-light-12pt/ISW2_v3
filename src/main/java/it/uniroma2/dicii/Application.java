package it.uniroma2.dicii;

import it.uniroma2.dicii.analysis.SonarAnalysisExecutor;
import it.uniroma2.dicii.analysis.model.SonarAnalysisResult;
import it.uniroma2.dicii.export.DatasetManager;
import it.uniroma2.dicii.issueManagement.exceptions.VersionsException;
import it.uniroma2.dicii.issueManagement.model.ResolutionType;
import it.uniroma2.dicii.issueManagement.model.TicketFilter;
import it.uniroma2.dicii.issueManagement.model.TicketStatus;
import it.uniroma2.dicii.issueManagement.model.TicketType;
import it.uniroma2.dicii.issueManagement.proportion.ProportionApplier;
import it.uniroma2.dicii.issueManagement.proportion.ProportionApplierImpl;
import it.uniroma2.dicii.issueManagement.ticket.JiraTicketsManager;
import it.uniroma2.dicii.issueManagement.ticket.TicketsManager;
import it.uniroma2.dicii.issueManagement.version.JiraVersionsManager;
import it.uniroma2.dicii.issueManagement.version.VersionsManager;
import it.uniroma2.dicii.metrics.CompositeMetricsExtractor;
import it.uniroma2.dicii.metrics.impl.CKMetricsExtractor;
import it.uniroma2.dicii.metrics.impl.JavaParserMetricsExtractor;
import it.uniroma2.dicii.metrics.impl.SonarMetricsExtractor;
import it.uniroma2.dicii.metrics.impl.VCSMetricsExtractor;
import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import it.uniroma2.dicii.vcsManagement.commit.GitCheckoutManager;
import it.uniroma2.dicii.vcsManagement.commit.GitCommitManager;
import it.uniroma2.dicii.vcsManagement.exception.CommitException;
import it.uniroma2.dicii.vcsManagement.exception.TagRetrievalException;
import it.uniroma2.dicii.vcsManagement.model.Tag;
import it.uniroma2.dicii.vcsManagement.tags.GitTagsManager;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class Application {

    private final String projectName;
    private final String repoPath;
    private final String outputPath;

    public Application(String projectName, String repoPath, String outputPath) {
        this.repoPath = repoPath;
        this.projectName = projectName;
        if (outputPath.endsWith("/")) this.outputPath = outputPath + projectName + ".csv";
        else this.outputPath = outputPath + "/" + projectName + ".csv";
    }

    public void execute(boolean verbose) {
        log.info("Starting process...");
        log.info("Project name: {}", projectName);
        log.info("Repository path: {}", repoPath);

        try {
            // Gets versions managed on Jira
            VersionsManager versionsManager = new JiraVersionsManager();
            versionsManager.getVersionsInfo();
            // Since unreleased versions don't have a corresponding tag on GitHub, they are not treated
            versionsManager.removeUnreleasedVersions();

            if (verbose) versionsManager.listVersions();

            TicketsManager ticketsManager = new JiraTicketsManager(versionsManager);

            TicketFilter filter = buildTicketFilter();
            ticketsManager.retrieveTickets(filter);

            try (GitCommitManager gitCommitManager = new GitCommitManager(ticketsManager)) {
                retrieveCommits(gitCommitManager, ticketsManager);
                ticketsManager.setFixVersionToTickets();
                ticketsManager.removeTicketsWithNoFixVersion();
                applyProportions(versionsManager, ticketsManager);
                logUnusableTickets(ticketsManager);
            }

            // Retrieves tags from Git; these will be used to apply git checkout at specific versions
            GitTagsManager tagsManager = new GitTagsManager();
            tagsManager.retrieveTags();
            List<Tag> tags = tagsManager.getTags();
            tags.forEach(t -> log.info("Tag {} at commit id {}", t.getTagName(), t.getAssociatedCommitId()));

            // This object executes `git checkout` at a specific commit
            GitCheckoutManager checkoutManager = new GitCheckoutManager();
            SonarAnalysisExecutor analysisManager = new SonarAnalysisExecutor(this.repoPath);
            CompositeMetricsExtractor compositeExtractor;
            DatasetManager datasetManager = new DatasetManager(this.outputPath);
            datasetManager.initDataset();
            for (int i = 0; i < tags.size(); i++) {
                // 1. Checkout to the desired version
                checkoutManager.checkOutProjectAtCommit(tags.get(i).getAssociatedCommitId());

                // 2. Run Sonar Analysis on SonarCloud
                List<SonarAnalysisResult> sonarResults = analysisManager.executeAnalysisAtCommit(tags.get(i).getAssociatedCommitId());

                // 3. Prepare the Composite Extractor
                compositeExtractor = new CompositeMetricsExtractor();

                // 4. Add the Workers
                // A. Static Metrics (CK)
                compositeExtractor.addExtractor(new CKMetricsExtractor(true, Integer.MAX_VALUE, true));

                // B. Process Metrics (VCS)
                // Requires previous commit for Churn. For the very first commit, previous is null.
                String previousCommit = (i > 0) ? tags.get(i - 1).getAssociatedCommitId() : null;
                compositeExtractor.addExtractor(new VCSMetricsExtractor(previousCommit));

                compositeExtractor.addExtractor(new JavaParserMetricsExtractor());

                // C. Quality Metrics (Sonar)
                // Passes the list we just fetched so it can be mapped to methods
                if (sonarResults != null && !sonarResults.isEmpty()) {
                    compositeExtractor.addExtractor(new SonarMetricsExtractor(sonarResults));
                } else {
                    log.error("No results were retrieved from SonarCloud. Cannot execute Sonar metrics extraction.");
                }

                // 5. Add version results to the dataset
                List<MeasuredMethod> measuredMethods = compositeExtractor.extractMetrics();
                datasetManager.appendToDataset(tags.get(i).getTagName(), measuredMethods);
                log.info("Round completed for version {}", tags.get(i).getTagName());
            }
        } catch (VersionsException e) {
            log.error("Error retrieving versions: {}", e.getMessage(), e);
        } catch (CommitException | IOException e) {
            log.error("Error retrieving commits: {}", e.getMessage(), e);
        } catch (TagRetrievalException e) {
            log.error("Error retrieving tags: {}", e.getMessage(), e);
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
     * @param ticketsManager   the tickets manager to retrieve tickets from
     * @throws CommitException if an error occurs while retrieving commits
     * @throws IOException     if an error occurs while retrieving the repository
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
     * @param ticketsManager  the tickets manager to retrieve tickets from
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
        ticketsManager.getTickets().stream().filter(t -> t.getInjected() == null || t.getOpening() == null || t.getFixed() == null).forEach(t -> log.debug("Ticket {}; \t iv: {}; \t op: {}; \t fx: {}", t.getKey(), t.getInjected(), t.getOpening(), t.getFixed()));
    }
}