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

    private final String projectPath;

    public GitCheckoutManager() {
        this.projectPath = PropertiesManager.getInstance().getProperty("project.repo.path");
    }

    public void checkOutProjectAtCommit(String commitId) {
        try (Git git = Git.open(new File(projectPath))) {
            CheckoutCommand checkout = git.checkout().setName(commitId);
            checkout.call();
            log.info("Successfully checked out to commit: {}", commitId);
        } catch (GitAPIException | IOException e) {
            log.error("Exception occurred while checking out to commit {}: ", commitId, e);
        }
    }
}