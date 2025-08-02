package it.uniroma2.dicii.vcsManagement.commit;

import com.google.common.annotations.VisibleForTesting;
import it.uniroma2.dicii.issueManagement.exceptions.NoTicketsFoundException;
import it.uniroma2.dicii.issueManagement.model.Ticket;
import it.uniroma2.dicii.issueManagement.model.Version;
import it.uniroma2.dicii.issueManagement.ticket.TicketsManager;
import it.uniroma2.dicii.issueManagement.version.VersionsManager;
import it.uniroma2.dicii.properties.PropertiesManager;
import it.uniroma2.dicii.vcsManagement.exception.CommitException;
import it.uniroma2.dicii.vcsManagement.model.CommitInfo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GitCommitManager {

    private final String projectName;

    private final Repository repository;
    private final Git git;
    private final Pattern ticketPatternWithProjectName;
    private final Pattern ticketPatternWithIssue;
    private final Pattern ticketPatternWithHashtag;
    private final TicketsManager ticketsManager;
    private final VersionsManager versionsManager;

    /**
     * Creates a new Git Commit Manager for the specified repository path and project name
     *
     * @throws IOException if the repository can't be accessed
     */
    public GitCommitManager(VersionsManager versionsManager, TicketsManager ticketsManager) throws IOException {
        this.ticketsManager = ticketsManager;
        this.versionsManager = versionsManager;
        this.projectName = PropertiesManager.getInstance().getProperty("project.name").toUpperCase(Locale.ROOT);

        // Initialize the repository
        String repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(repoPath + "/.git")).readEnvironment().findGitDir().build();

        git = new Git(repository);

        this.ticketPatternWithProjectName = Pattern.compile("(" + projectName + "-\\d+)");
        this.ticketPatternWithIssue = Pattern.compile("(ISSUE\\s\\d+)");
        this.ticketPatternWithHashtag = Pattern.compile("(#\\d+)");
    }

    /**
     * Retrieves all commits from the repository and associates them with ticket IDs
     */
    public void getCommitsWithTickets() throws CommitException {
        try {
            // Retrieves all the tickets from the tickets manager
            List<Ticket> tickets = ticketsManager.getTickets();

            if (tickets.isEmpty()) throw new NoTicketsFoundException("No available tickets to associate commits with");

            // Executes the `git log` command to retrieve all commits from the repository
            LogCommand logCommand = git.log();
            Iterable<RevCommit> commits = logCommand.call();

            // Iterate through all commits and extract ticket IDs from commit messages
            for (RevCommit commit : commits) {
                String commitMessage = commit.getFullMessage();
                List<String> ticketIds = extractTicketIds(commitMessage);

                if (ticketIds.isEmpty())
                    // This is a debug message because this condition may be very common, and it's not necessary to log it every time
                    log.debug("No ticket IDs found in commit {}. Message: {}", commit.getId(), commitMessage);

                CommitInfo commitInfo = new CommitInfo(commit.getName(), commit.getAuthorIdent().getName(), commit.getAuthorIdent().getEmailAddress(), LocalDate.ofInstant(Instant.ofEpochSecond(commit.getCommitTime()), ZoneId.systemDefault()), commitMessage);

                // For each ticket ID found in the commit message
                for (String ticketId : ticketIds) {
                    // Check if any ticket in the ticket list has a matching key
                    int i = 0;
                    while (i < tickets.size() && !ticketId.equalsIgnoreCase(tickets.get(i).getKey())) i++;
                    if (i < tickets.size()) {
                        log.info("Ticket {} found matching any of the following patterns: {}.", ticketId, ticketIds);
                        tickets.get(i).addCommit(commitInfo);
                    } else {
                        log.warn("No ticket of type found matching any of the following patterns: {}.", ticketId);
                    }
                }
            }
        } catch (GitAPIException e) {
            throw new CommitException("Unable to retrieve commits: error accessing Git repository", e);
        } catch (NoTicketsFoundException e) {
            throw new CommitException("Unable to retrieve commits: no ticket to be associated", e);
        }
    }

    /**
     * Extracts ticket IDs from a commit message
     *
     * @param commitMessage the commit message to search
     * @return list of ticket IDs found in the commit message
     */
    @VisibleForTesting
    protected List<String> extractTicketIds(String commitMessage) {
        List<String> ticketIds = new ArrayList<>();

        // Using the uppercase commit message to count all occurrences of the ticket IDs
        Matcher matcher = ticketPatternWithProjectName.matcher(commitMessage.toUpperCase());
        while (matcher.find()) if (!ticketIds.contains(matcher.group())) ticketIds.add(matcher.group());

        matcher = ticketPatternWithIssue.matcher(commitMessage.toUpperCase());
        while (matcher.find()) if (!ticketIds.contains(matcher.group()))
            ticketIds.add(matcher.group().replace("ISSUE ", projectName.concat("-")).trim());

        matcher = ticketPatternWithHashtag.matcher(commitMessage.toUpperCase());
        while (matcher.find()) if (!ticketIds.contains(matcher.group()))
            ticketIds.add(matcher.group().replace("#", projectName.concat("-")).trim());

        if (commitMessage.toLowerCase().matches("merge"))
            log.info("Merge commit found. Message: {}", commitMessage);

        return ticketIds;
    }

    /**
     * Closes the Git repository
     */
    public void close() {
        git.close();
        repository.close();
    }

    public List<CommitInfo> retrieveVersionsCommits() {
        List<CommitInfo> commitsWithVersion = new ArrayList<>();
        try {
            List<Ref> tagList = git.tagList().call();
            RevWalk revWalk = new RevWalk(repository);
            for (Ref tagRef : tagList) {
                String found = tagRef.getName().split("-")[1];
                Version version = versionsManager.getVersionByName(found);
                if (version == null) {
                    continue;
                }

                log.info("Retrieving commits for version {}", tagRef.getName());

                ObjectId objectId = tagRef.getObjectId(); // Can be tag or commit
                RevObject obj = revWalk.parseAny(objectId);

                RevCommit commit;
                if (obj instanceof RevTag) {
                    commit = revWalk.parseCommit(((RevTag) obj).getObject());
                } else {
                    commit = revWalk.parseCommit(objectId);
                }

                Iterable<RevCommit> commits = git.log().add(commit.getId()).call();
                commits.forEach(revCommit -> {
                    CommitInfo commitInfo = new CommitInfo(revCommit.getName(), revCommit.getAuthorIdent().getName(), revCommit.getAuthorIdent().getEmailAddress(), LocalDate.ofInstant(Instant.ofEpochSecond(revCommit.getCommitTime()), ZoneId.systemDefault()), revCommit.getFullMessage());
                    commitInfo.setVersion(version);
                    commitsWithVersion.add(commitInfo);
                });
            }
        } catch (Exception e) {
            log.error("Error accessing Git repository: {}", e.getMessage(), e);
        }
        return commitsWithVersion;
    }
}