package it.uniroma2.dicii.metrics.impl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.uniroma2.dicii.metrics.MetricsExtractor;
import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import it.uniroma2.dicii.metrics.model.MetricsExtractorType;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public class VCSMetricsExtractor implements MetricsExtractor {

    private final String repoPath;
    private final String previousCommitId; // Essential for Churn calculation

    public VCSMetricsExtractor(String previousCommitId) {
        this.repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        this.previousCommitId = previousCommitId;
    }

    @Override
    public List<MeasuredMethod> extractMetrics() {
        List<MeasuredMethod> results = new ArrayList<>();

        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).build(); Git git = new Git(repository)) {

            // 1. Walk all Java non-test files in the current checkout
            try (Stream<Path> paths = Files.walk(Paths.get(repoPath))) {
                paths.filter(p -> p.toString().endsWith(".java") && !p.toString().toLowerCase().contains("test") && !p.toString().contains("/target/")).forEach(path -> {
                    try {
                        // 2. Parse the file to find Methods and their line numbers
                        CompilationUnit cu = StaticJavaParser.parse(path);
                        String relativePath = getRelativePath(path);
                        if (!relativePath.contains("src/test/java")) {
                            String fullyQualifiedNamePrefix = relativePath.split("src/main/java/")[1].replace("/", ".").replace(".java", ".");

                            cu.findAll(MethodDeclaration.class).forEach(method -> {
                                if (method.getBegin().isPresent() && method.getEnd().isPresent()) {
                                    // Only analyzes class methods, excluding interfaces
                                    if (method.getBody().isPresent()) {
                                        int startLine = method.getBegin().get().line;
                                        int endLine = method.getEnd().get().line;

                                        MeasuredMethod mm = new MeasuredMethod();
                                        mm.setExtractedFrom(MetricsExtractorType.VCS);
                                        // Ensure this naming matches your CK naming for the merge to work
                                        mm.setMethodName(MethodNameGenerator.generateMethodName(fullyQualifiedNamePrefix + method.getNameAsString(), startLine));

                                        // 3. Calculate Developer Count (Lifetime - via Blame)
                                        mm.setDeveloperCount(calculateDeveloperCount(git, relativePath, startLine, endLine));

                                        // 4. Calculate Churn (Process - via Diff vs. Previous Commit)
                                        if (previousCommitId != null) {
                                            mm.setChurn(calculateChurn(repository, relativePath, startLine, endLine));
                                        }
                                        results.add(mm);
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process file for VCS metrics: {}", path, e);
                    }
                });
            }

        } catch (IOException e) {
            log.error("VCS Extraction failed", e);
        }

        return results;
    }

    /**
     * Calculates the number of unique developers who have contributed to a specific range of lines
     * within a given file in a Git repository.
     *
     * @param git       the Git object to interact with the repository
     * @param path      the path of the file in the repository to analyze
     * @param startLine the starting line of the range to analyze (inclusive), 1-indexed
     * @param endLine   the ending line of the range to analyze (inclusive), 1-indexed
     * @return the count of unique developers who authored the specified range of lines, or 0 if an error occurs during the operation
     */
    private int calculateDeveloperCount(Git git, String path, int startLine, int endLine) {
        try {
            log.debug("Calculating developer count for file {} (rows {}-{})", path, startLine, endLine);
            BlameCommand blame = git.blame().setFilePath(path);
            BlameResult result = blame.call();
            if (result == null) return 0;

            Set<String> developers = new HashSet<>();
            // Blame is 0-indexed, AST is 1-indexed
            for (int i = startLine - 1; i < endLine; i++) {
                if (i < result.getResultContents().size()) {
                    developers.add(result.getSourceAuthor(i).getName());
                }
            }
            return developers.size();
        } catch (GitAPIException e) {
            log.warn("Failed to calculate developer count for file {}: {}", path, e.getMessage());
            return 0;
        }
    }

    /**
     * Calculates the churn metric for a specific section of a file within a Git repository.
     * The churn metric is defined as the sum of lines added and deleted within the specified range of lines.
     *
     * @param repo      the Git repository to analyze
     * @param path      the path of the file in the repository for which churn is being calculated
     * @param startLine the starting line of the section to analyze
     * @param endLine   the ending line of the section to analyze
     * @return the churn value, which is the sum of lines added and deleted within the specified section
     */
    private int calculateChurn(Repository repo, String path, int startLine, int endLine) {
        log.debug("Calculating churn for file {} (rows {}-{})", path, startLine, endLine);
        if (previousCommitId == null) return 0;
        try {
            ObjectId oldHead = repo.resolve(previousCommitId + "^{tree}");
            ObjectId newHead = repo.resolve("HEAD^{tree}");

            try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                formatter.setRepository(repo);
                formatter.setDiffComparator(RawTextComparator.DEFAULT);
                formatter.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(path));

                List<DiffEntry> diffs = formatter.scan(oldHead, newHead);
                int churn = 0;

                for (DiffEntry entry : diffs) {
                    // Determine edited lines from the "Hunks"
                    for (Edit edit : formatter.toFileHeader(entry).toEditList()) {
                        // We only care if the edit overlaps with our method's current lines
                        // Note: This is an approximation. Ideally, we map lines back.
                        // But for simple churn, checking overlap with current method bounds is a standard heuristic.
                        if (isOverlapping(edit, startLine, endLine)) {
                            // Churn = Lines Added + Lines Deleted
                            churn += (edit.getEndB() - edit.getBeginB()) + (edit.getEndA() - edit.getBeginA());
                        }
                    }
                }
                return churn;
            }
        } catch (IOException e) {
            log.warn("Failed to calculate churn for file {}: {}", path, e.getMessage());
            return 0;
        }
    }

    /**
     * Determines whether the specified edit overlaps with the given range of lines.
     *
     * @param edit      the edit object containing the range of lines to check for overlap
     * @param startLine the starting line of the range to check against, 1-indexed
     * @param endLine   the ending line of the range to check against, 1-indexed
     * @return true if the edit overlaps with the specified line range; false otherwise
     */
    private boolean isOverlapping(Edit edit, int startLine, int endLine) {
        // Edit coordinates are 0-indexed. Method is 1-indexed.
        // Check the intersection of [editBegin, editEnd] and [methodStart, methodEnd]
        int editStart = edit.getBeginB() + 1;
        int editEnd = edit.getEndB() + 1;
        return Math.max(startLine, editStart) <= Math.min(endLine, editEnd);
    }

    /**
     * Computes the relative path of the given {@code path} with respect to the repository path.
     *
     * @param path the full path to be converted to a relative path
     * @return the relative path as a {@code String}, or an empty string if the path cannot be relativized
     */
    private String getRelativePath(Path path) {
        return new File(repoPath).toURI().relativize(path.toUri()).getPath();
    }
}
