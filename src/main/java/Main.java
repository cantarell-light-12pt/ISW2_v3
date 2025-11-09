import it.uniroma2.dicii.Application;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

@Slf4j
public class Main {
    public static void main(String[] args) throws GitAPIException, IOException {
        boolean verbose = args.length > 0 && args[0].equals("-v");

        String repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        String projectName = PropertiesManager.getInstance().getProperty("project.name");

        /*Repository repository = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).readEnvironment().findGitDir().build();

        Git git = new Git(repository);

        git.branchList().call().forEach(ref -> log.info("Branch: {}", ref.getName()));*/

        Application app = new Application(projectName, repoPath);
        app.execute(verbose);
    }
}
