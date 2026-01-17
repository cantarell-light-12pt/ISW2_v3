package it.uniroma2.dicii.metrics.impl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import it.uniroma2.dicii.metrics.MetricsExtractor;
import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import it.uniroma2.dicii.metrics.model.MetricsExtractorType;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
public class JavaParserMetricsExtractor implements MetricsExtractor {

    private final String repoPath;

    public JavaParserMetricsExtractor() {
        this.repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
    }

    @Override
    public List<MeasuredMethod> extractMetrics() {
        log.info("Extracting metrics from Java files...");
        List<MeasuredMethod> results = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get(repoPath))) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(path);
                    String relativePath = getRelativePath(path);
                    if (!relativePath.contains("src/test/java") && !relativePath.contains("/target/")) {
                        String fullyQualifiedNamePrefix = relativePath.split("src/main/java/")[1].replace("/", ".").replace(".java", ".");

                        cu.findAll(MethodDeclaration.class).forEach(method -> {
                            // Only analyzes class methods, excluding interfaces
                            if (method.getBody().isPresent()) {
                                MeasuredMethod mm = new MeasuredMethod();
                                mm.setExtractedFrom(MetricsExtractorType.JAVA_PARSER);
                                int startLine = 0;
                                if (method.getBegin().isPresent()) startLine = method.getBegin().get().line;
                                mm.setMethodName(MethodNameGenerator.generateMethodName(fullyQualifiedNamePrefix + method.getNameAsString(), startLine));

                                // Computes Comment Density and Cognitive Complexity
                                mm.setCommentDensity(calculateCommentDensity(method));
                                mm.setCognitiveComplexity(calculateCognitiveComplexity(method));

                                results.add(mm);
                            }
                        });
                    }
                } catch (IOException e) {
                    log.warn("Error parsing file {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Error walking project files", e);
        }
        return results;
    }

    /**
     * Calculates the comment density of a given method.
     * Comment density is the ratio between the number of lines occupied by comments
     * (including both inline comments and Javadocs comments, if any) to the total
     * number of lines covered by the method.
     *
     * @param method the method for which the comment density is to be calculated.
     *               It must be a valid {@code MethodDeclaration} containing
     *               location information (start and end lines).
     * @return the comment density as a {@code double} value ranging from 0.0 to 1.0.
     * Returns 0.0 if the method is invalid or has no lines of code.
     */
    private double calculateCommentDensity(MethodDeclaration method) {
        if (method.getBegin().isEmpty() || method.getEnd().isEmpty()) return 0.0;

        int startLine = method.getBegin().get().line;
        int endLine = method.getEnd().get().line;
        int totalLines = endLine - startLine + 1;

        if (totalLines == 0) return 0.0;

        // 1. Add internal comments
        Set<Comment> comments = new HashSet<>(method.getAllContainedComments());

        // 2. Add attached Javadoc (if any)
        method.getComment().ifPresent(comments::add);

        // 3. Count unique lines covered by comments
        int commentLines = comments.stream().mapToInt(c -> {
            if (c.getBegin().isPresent() && c.getEnd().isPresent()) {
                return c.getEnd().get().line - c.getBegin().get().line + 1;
            }
            return 0;
        }).sum();

        // 4. Calculate Density (0.0 to 1.0)
        // Note: We use totalLines as the denominator to represent "Density within the total space"
        return (double) commentLines / totalLines;
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

    /**
     * Calculates Cognitive Complexity.
     * Rules:
     * - +1 for breaks in linear flow (if, else, for, while, do, catch, switch, etc.)
     * - +1 for boolean sequences (&&, ||)
     * - +Nesting Level for each structure that increases nesting.
     */
    private int calculateCognitiveComplexity(MethodDeclaration method) {
        CognitiveComplexityVisitor visitor = new CognitiveComplexityVisitor();
        visitor.visit(method, 0); // Start with nesting level 0
        return visitor.getComplexity();
    }

    /**
     * Visitor that computes the score based on SonarSource's Cognitive Complexity white paper.
     */
    @Getter
    private static class CognitiveComplexityVisitor extends VoidVisitorAdapter<Integer> {

        private int complexity = 0;

        @Override
        public void visit(IfStmt n, Integer nesting) {
            complexity += 1 + nesting;
            super.visit(n, nesting); // Visit condition

            // The 'then' block gets increased nesting
            n.getThenStmt().accept(this, nesting + 1);

            // Handle 'else'
            if (n.getElseStmt().isPresent()) {
                Statement elseStmt = n.getElseStmt().get();

                // Optimization: "else if" is treated as a single structure (no extra nesting penalty usually)
                // In JavaParser, "else if" is an IfStmt inside the elseStmt of the parent.
                if (elseStmt.isIfStmt()) {
                    elseStmt.accept(this, nesting); // Keep same nesting level for "else if"
                } else {
                    complexity += 1 + nesting; // "else" costs +1 + current nesting
                    elseStmt.accept(this, nesting + 1);
                }
            }
        }

        @Override
        public void visit(ForStmt n, Integer nesting) {
            complexity += 1 + nesting;
            super.visit(n, nesting + 1);
        }

        @Override
        public void visit(ForEachStmt n, Integer nesting) {
            complexity += 1 + nesting;
            super.visit(n, nesting + 1);
        }

        @Override
        public void visit(WhileStmt n, Integer nesting) {
            complexity += 1 + nesting;
            super.visit(n, nesting + 1);
        }

        @Override
        public void visit(DoStmt n, Integer nesting) {
            complexity += 1 + nesting;
            super.visit(n, nesting + 1);
        }

        @Override
        public void visit(CatchClause n, Integer nesting) {
            complexity += 1 + nesting;
            super.visit(n, nesting + 1);
        }

        @Override
        public void visit(SwitchEntry n, Integer nesting) {
            // Cases usually don't increment nesting level in strictly structural views,
            // but they DO add to complexity (+1 per case)
            if (n.getLabels().isNonEmpty()) { // Don't count "default"
                complexity += 1 + nesting;
            }
            super.visit(n, nesting + 1);
        }

        @Override
        public void visit(BinaryExpr n, Integer nesting) {
            // Sonar Rule: A sequence of binary operators adds to complexity.
            // A && B && C => +1
            // A && B || C => +2 (change of operator)

            boolean isBooleanOp = n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR;

            if (isBooleanOp) {
                // Check if this is part of an existing sequence in the PARENT
                boolean isSameAsParent = false;
                if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof BinaryExpr parent) {
                    if (parent.getOperator() == n.getOperator()) {
                        isSameAsParent = true;
                    }
                }

                if (!isSameAsParent) {
                    complexity++;
                }
            }
            super.visit(n, nesting);
        }

        @Override
        public void visit(ConditionalExpr n, Integer nesting) {
            complexity += 1 + nesting;
            super.visit(n, nesting + 1);
        }
    }
}