package it.uniroma2.dicii.metrics.impl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.uniroma2.dicii.analysis.model.SonarAnalysisResult;
import it.uniroma2.dicii.metrics.MetricsExtractor;
import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import it.uniroma2.dicii.metrics.model.MetricsExtractorType;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class SonarMetricsExtractor implements MetricsExtractor {

    private final String repoPath;
    private final List<SonarAnalysisResult> sonarIssues;

    /**
     * @param sonarIssues The raw list of issues retrieved from SonarCloud for this specific version.
     */
    public SonarMetricsExtractor(List<SonarAnalysisResult> sonarIssues) {
        this.repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        this.sonarIssues = sonarIssues;
    }

    @Override
    public List<MeasuredMethod> extractMetrics() {
        List<MeasuredMethod> results = new ArrayList<>();

        if (sonarIssues == null || sonarIssues.isEmpty()) {
            return results;
        }

        // 1. Walk through all files in the repository
        try (Stream<Path> paths = Files.walk(Paths.get(repoPath))) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    File file = path.toFile();
                    String relativePath = getRelativePath(path);
                    if (!relativePath.contains("src/test/java")) {
                        String fullyQualifiedNamePrefix = relativePath.split("src/main/java/")[1].replace("/", ".").replace(".java", ".");

                        // 2. Parse the file to find Method Boundaries
                        CompilationUnit cu = StaticJavaParser.parse(file);

                        // 3. Filter Sonar issues belonging to this file
                        // Note: Sonar 'component' keys usually look like "projectKey:src/main/java/..."
                        // We check if the component key contains our relative path.
                        List<SonarAnalysisResult> fileIssues = sonarIssues.stream().filter(issue -> issue.getComponent().endsWith(relativePath)).toList();

                        if (fileIssues.isEmpty()) return;

                        // 4. Map Issues to Methods
                        cu.findAll(MethodDeclaration.class).forEach(method -> mapIssuesToMethods(fullyQualifiedNamePrefix, method, fileIssues, results));
                    }
                } catch (Exception e) {
                    log.warn("Error processing file for Sonar metrics: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to walk project files", e);
        }
        return results;
    }

    /**
     * Maps a list of sonar analysis issues to the methods in the source code.
     * For each issue within the method's line range, the smell count is incremented
     * based on the severity level, and the results are stored in a list of measured methods.
     *
     * @param method     the method being analyzed, including its start and end line numbers
     * @param fileIssues a list of issues reported by the Sonar analysis tool for the file
     * @param results    a list where the resulting measured methods, including metrics and smells, will be added
     */
    private void mapIssuesToMethods(String fullyQualifiedMethodNamePrefix, MethodDeclaration method, List<SonarAnalysisResult> fileIssues, List<MeasuredMethod> results) {
        if (method.getBegin().isPresent() && method.getEnd().isPresent()) {
            int startLine = method.getBegin().get().line;
            int endLine = method.getEnd().get().line;

            MeasuredMethod mm = new MeasuredMethod();
            mm.setExtractedFrom(MetricsExtractorType.SONAR);
            mm.setMethodName(MethodNameGenerator.generateMethodName(fullyQualifiedMethodNamePrefix + method.getNameAsString(), startLine));

            // Count smells strictly within this method's body
            for (SonarAnalysisResult issue : fileIssues)
                if (issue.getLine() >= startLine && issue.getLine() <= endLine) {
                    if (issue.getType().equals("CODE_SMELL"))
                        incrementSmellCount(mm, issue.getSeverity());
                    if (issue.getType().equals("BUG"))
                        mm.incrementOrSetDefectCount();
                }
            results.add(mm);
        }
    }

    /**
     * Increments the specified smell count in a {@code MeasuredMethod} object based on the given severity level.
     * Smell severities are mapped to specific counters: BLOCKER, CRITICAL, MAJOR, MINOR, INFO.
     * Fallback logic is applied for unspecified severities.
     *
     * @param mm       the {@code MeasuredMethod} whose smell count will be incremented
     * @param severity the severity level of the smell (e.g., "BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO")
     */
    private void incrementSmellCount(MeasuredMethod mm, String severity) {
        // Map Sonar Severity strings to MeasuredMethod fields
        // Severities: BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        switch (severity.toUpperCase()) {
            case "BLOCKER" -> mm.incrementOrSetBlockerSmellsCount();
            case "CRITICAL" -> mm.incrementOrSetCriticalSmellsCount(); // Mapping Critical -> Major if you lack a Critical field
            case "MAJOR" -> mm.incrementOrSetMajorSmellsCount();
            case "MINOR" -> mm.incrementOrSetMinorSmellsCount();
            case "INFO" -> mm.incrementOrSetInfoSmellsCount();
            default -> log.warn("Unknown Sonar smell severity: {}. Skipping.", severity);
        }
    }

    /**
     * Converts the given absolute path into a relative path with respect to the repository's root directory.
     *
     * @param path the absolute path of the file or directory to be converted to a relative path
     * @return the relative path as a string, calculated from the repository's root directory
     */
    private String getRelativePath(Path path) {
        // e.g., src/main/java/com/example/MyClass.java
        return new File(repoPath).toURI().relativize(path.toUri()).getPath();
    }
}