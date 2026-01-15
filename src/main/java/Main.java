import it.uniroma2.dicii.Application;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        boolean verbose = args.length > 0 && args[0].equals("-v");

        String repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        String projectName = PropertiesManager.getInstance().getProperty("project.name");

        Application app = new Application(projectName, repoPath);
        app.execute(verbose);
    }
}
