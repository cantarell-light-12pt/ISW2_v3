package it.uniroma2.dicii.vcsManagement.commit;

import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;

@Slf4j
public class GitCheckoutManager {

    private final String projectRepoPath;

    public GitCheckoutManager() {
        this.projectRepoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
    }

    /**
     * Checks out the project to the given commit ID
     *
     * @param commitId the commit ID to which the project is checked out
     */
    public void checkOutProjectAtCommit(String commitId) {
        try (Git git = Git.open(new File(projectRepoPath))) {
            CheckoutCommand checkout = git.checkout().setName(commitId);
            checkout.call();
            log.info("Successfully checked out to commit: {}", commitId);
        } catch (GitAPIException | IOException e) {
            log.error("Exception occurred while checking out to commit {}: ", commitId, e);
        }
    }
}