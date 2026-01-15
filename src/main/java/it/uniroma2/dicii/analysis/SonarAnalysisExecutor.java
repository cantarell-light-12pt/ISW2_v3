package it.uniroma2.dicii.analysis;

import it.uniroma2.dicii.analysis.model.SonarAnalysisResult;
import it.uniroma2.dicii.jdk.JdkManager;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SonarAnalysisExecutor {

    private final String repoPath;

    public SonarAnalysisExecutor(String repoPath) {
        this.repoPath = repoPath;
    }

    /**
     * Executes an analysis on the specified commit by checking out the repository at the given commit,
     * detecting the required Java version, resolving the appropriate JDK path, and running the analysis
     * using the SonarScanner.
     *
     * @param commitId the ID of the commit to analyze
     */
    public List<SonarAnalysisResult> executeAnalysisAtCommit(String commitId) {
        log.info("--------------------------------------------------");
        log.info("Processing commit {}", commitId);

        // 1. Detect Required Java Version
        String javaVersion = detectJavaVersion();
        log.info("Detected required Java version: {}", javaVersion);

        // 2. Resolve JDK Path from Properties
        String jdkPath = JdkManager.getJdkPathForVersion(javaVersion);
        if (jdkPath == null) {
            log.warn("No configured JDK found for version '{}'. Using system default.", javaVersion);
        } else {
            log.info("Using JDK at: {}", jdkPath);
        }

        // 4. Run Analysis with a specific JDK
        try {
            // 1. Executes `mvn clean verify` to build the project with a specific Java version
            executeMavenInstall(jdkPath);
            // 2. Executes SonarQube analysis via the Maven plugin
            runSonarAnalysis(commitId);
            log.info("Analysis submitted for commit {}", commitId);

            // 5. [NEW] Get the Task ID
            String ceTaskId = extractCeTaskId();
            if (ceTaskId == null) {
                log.error("Could not retrieve analysis Task ID. Skipping issue retrieval.");
                return null;
            }

            SonarResultRetriever retriever = new SonarResultRetriever();
            boolean success = retriever.waitForAnalysisToComplete(ceTaskId);

            if (success) {
                String projectKey = System.getenv("SONAR_PROJECT_KEY");

                log.info("Retrieving analysis results...");
                List<SonarAnalysisResult> analysisResults = retriever.retrieveResults(projectKey);
                log.info("Successfully retrieved {} code smells.", analysisResults.size());
                return analysisResults;
            } else {
                log.error("Analysis did not complete successfully.");
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to analyze commit {}: {}", commitId, e.getMessage());
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
        return null;
    }

    /**
     * Uses Maven to evaluate the 'maven.compiler.source' property.
     * This handles inheritance from parent POMs correctly.
     */
    private String detectJavaVersion() {
        try {
            // 1. Try to read the property
            ProcessBuilder pb = new ProcessBuilder("mvn", "help:evaluate", "-Dexpression=maven.compiler.source", "-q", "-DforceStdout");
            log.debug("Executing command: {}", pb.command());
            pb.directory(new File(repoPath));
            Process process = pb.start();
            return extractVersionFromProcessOutput(process);
        } catch (Exception e) {
            log.error("Error detecting Java version: {}. Falling back to 1.8 (default)", e.getMessage());
            return "1.8"; // Safe fallback for BookKeeper
        }
    }

    /**
     * Extracts a version string from the output of a given process. The method reads
     * the output stream of the process, filters out unwanted lines (e.g., blank lines,
     * lines containing specific errors, or lines starting with a bracket), and retrieves
     * the cleaned version string. If no valid version string is found, it returns the
     * default version "1.8".
     *
     * @param process the process whose output stream will be read to extract the version string
     * @return the extracted version string, or "1.8" as a default value if no valid string is found
     * @throws IOException if an I/O error occurs while reading the process output stream
     */
    private String extractVersionFromProcessOutput(Process process) throws IOException {
        String version = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null)
                // Filter out Maven noise and the specific error message
                if (!line.isBlank() && !line.contains("null object or invalid expression") && !line.startsWith("["))
                    version = line.trim();
        }

        if (version.isEmpty()) {
            // You could add a second ProcessBuilder call here for 'maven.compiler.target'
            // but usually if source is missing, target is too.
            log.warn("Property 'maven.compiler.source' not found. Falling back to default.");
            return "1.8";
        }
        return version;
    }

    /**
     * Executes a SonarQube analysis using Maven and submits the results to a specified SonarQube server.
     * <p>
     * The method retrieves necessary parameters such as the SonarQube server URL, project key, organization,
     * and authentication token from the environment variables. It constructs a Maven-based command to
     * perform the analysis and runs it in the repository's working directory.
     * </p><p>
     * Environment variables:
     * <ul>
     * <li>SONAR_TOKEN: The authentication token for SonarQube.</li>
     * <li>SONAR_PROJECT_KEY: The key associated with the SonarQube project.</li>
     * <li>SONAR_HOST_URL: The URL of the SonarQube server (default is "<a href="http://localhost:9000">localhost:9000</a>" if not provided).</li>
     * <li>SONAR_ORG: The organization key, if applicable.</li>
     * </ul>
     * </p>
     *
     * @param commitId the ID of the commit being analyzed (used for reporting revision to SonarCloud)
     * @throws IOException          if an error occurs during the process execution or if Sonar analysis returns a non-zero exit code.
     * @throws InterruptedException if the current thread is interrupted while waiting for the process to complete.
     */
    private void runSonarAnalysis(String commitId) throws IOException, InterruptedException {
        String sonarToken = System.getenv("SONAR_TOKEN");
        String projectKey = System.getenv("SONAR_PROJECT_KEY");
        String sonarHost = System.getenv("SONAR_HOST_URL");
        if (sonarHost == null || sonarHost.isBlank()) sonarHost = "http://localhost:9000";

        String sonarOrg = System.getenv("SONAR_ORG");

        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.add("sonar:sonar");
        command.add("-Dsonar.projectKey=" + projectKey);
        command.add("-Dsonar.host.url=" + sonarHost);
        command.add("-Dsonar.token=" + sonarToken);
        // Explicitly tell SonarCloud which commit revision this is
        command.add("-Dsonar.scm.revision=" + commitId);

        if (sonarOrg != null) {
            command.add("-Dsonar.organization=" + sonarOrg);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(repoPath));
        pb.redirectErrorStream(true);

        log.info("Executing Sonar Analysis with System Default JDK");

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) log.debug("[Sonar] {}", line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Sonar analysis exited with error code: " + exitCode);
        }
    }

    /**
     * Constructs a {@link ProcessBuilder} with the specified commands and optionally configures the environment
     * to use a specific JDK installation based on the provided JDK home directory.
     *
     * @param commands the list of commands to execute, where the first element represents the command and later
     *                 elements represent arguments to the command
     * @param jdkHome  the file system path to the JDK installation directory to be used by the process; if null, the
     *                 system default JDK is used
     * @return a {@link ProcessBuilder} configured with the specified commands and JDK environment settings
     */
    private ProcessBuilder buildProcessFromCommandsWithJdk(List<String> commands, String jdkHome) {
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(new File(repoPath));
        pb.redirectErrorStream(true);

        if (jdkHome != null) {
            pb.environment().put("JAVA_HOME", jdkHome);
            String path = jdkHome + "/bin" + File.pathSeparator + System.getenv("PATH");
            pb.environment().put("PATH", path);
        }
        return pb;
    }

    /**
     * Executes a Maven install build using the specified JDK. The 'install' command is necessary to install
     * project dependencies in the local .m2 directory and let the Sonar scanner find them.
     *
     * @param jdkHome the file system path to the JDK installation directory to be used;
     *                if null, the system default JDK is used
     * @throws IOException          if an I/O error occurs during execution
     * @throws InterruptedException if the process is interrupted during execution
     */
    private void executeMavenInstall(String jdkHome) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.add("clean");
        command.add("install");
        command.add("-DskipTests=true");

        ProcessBuilder pb = buildProcessFromCommandsWithJdk(command, jdkHome);
        log.info("Executing Maven Verify with JAVA_HOME={}", jdkHome != null ? jdkHome : "System Default");

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) log.debug("[Maven] {}", line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Maven build exited with error code: " + exitCode);
        }
    }

    /**
     * Reads the 'ceTaskId' from the report-task.txt file generated by the Maven Sonar Plugin.
     */
    private String extractCeTaskId() {
        // The standard location for the report file in a Maven project
        File reportFile = new File(repoPath + "/target/sonar/report-task.txt");

        if (!reportFile.exists()) {
            log.error("Report file not found at {}. Did the Sonar analysis run successfully?", reportFile.getAbsolutePath());
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(reportFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ceTaskId=")) {
                    return line.substring("ceTaskId=".length()).trim();
                }
            }
        } catch (IOException e) {
            log.error("Failed to read report-task.txt", e);
        }
        return null;
    }
}