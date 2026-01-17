package it.uniroma2.dicii.metrics.impl;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.mauricioaniche.ck.CKNotifier;
import it.uniroma2.dicii.metrics.MetricsExtractor;
import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import it.uniroma2.dicii.metrics.model.MetricsExtractorType;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CKMetricsExtractor implements MetricsExtractor {


    private final String repoPath;

    private final Boolean useJars;
    private final Integer maxAtOnce;
    private final Boolean variablesAndFields;

    public CKMetricsExtractor(Boolean useJars, Integer maxAtOnce, Boolean variablesAndFields) {
        repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        this.useJars = useJars;
        this.maxAtOnce = maxAtOnce;
        this.variablesAndFields = variablesAndFields;
    }

    @Override
    public List<MeasuredMethod> extractMetrics() {
        Map<String, CKMethodResult> extractedMetrics = extractMetricsWithCK(new CK(useJars, maxAtOnce, variablesAndFields));
        return convertToListOfMeasuredMethods(extractedMetrics);
    }

    /**
     * Extracts the metrics from the repository using the specified CK
     *
     * @param ck the CK object to use for the extraction
     */
    private Map<String, CKMethodResult> extractMetricsWithCK(CK ck) {
        Map<String, CKMethodResult> methodResults = new HashMap<>();

        ck.calculate(repoPath, new CKNotifier() {
            @Override
            public void notify(CKClassResult classResult) {
                // Skip interfaces and /target directory
                if (classResult.getType().equals("interface") || classResult.getFile().contains("/target/")) return;
                for (CKMethodResult methodResult : classResult.getMethods()) {
                    methodResults.put(MethodNameGenerator.generateMethodName(methodResult.getQualifiedMethodName().split("/")[0], methodResult.getStartLine()), methodResult);
                }
            }

            @Override
            public void notifyError(String sourceFilePath, Exception e) {
                log.error("Error analyzing file: {}. Exception: {} (cause: {})", sourceFilePath, e.getClass(), e.getMessage());
            }
        });
        log.info("Successfully extracted metrics for {} methods", methodResults.size());
        return methodResults;
    }

    /**
     * Converts a map of extracted method metrics into a list of {@code MeasuredMethod} objects.
     * The map keys represent the method names, and the values are {@code CKMethodResult} objects
     * containing various metrics for the methods.
     * <p>
     * The requested metrics obtainable from the CK library are:
     * <ul>
     * <li>cyclomatic complexity</li>
     * <li>max nesting depth</li>
     * <li>fan-in</li>
     * <li>fan-out</li>
     * <li>number of source lines of code</li>
     * <li>number of parameters</li>
     * <li>whether the method has Javadocs</li>
     * </ul>
     * </p>
     *
     * @param extractedMetrics a map where keys are method names and values are {@code CKMethodResult} objects
     *                         containing metrics such as cyclomatic complexity, max nesting depth, fan-in, fan-out, etc.
     * @return a list of {@code MeasuredMethod} objects populated with the corresponding metrics
     */
    private List<MeasuredMethod> convertToListOfMeasuredMethods(Map<String, CKMethodResult> extractedMetrics) {
        List<MeasuredMethod> measuredMethods = new ArrayList<>();
        MeasuredMethod measuredMethod;
        for (Map.Entry<String, CKMethodResult> entry : extractedMetrics.entrySet()) {
            measuredMethod = new MeasuredMethod();
            measuredMethod.setExtractedFrom(MetricsExtractorType.CK);
            measuredMethod.setMethodName(entry.getKey());
            measuredMethod.setCyclomaticComplexity(entry.getValue().getWmc());
            measuredMethod.setMaxNestingDepth(entry.getValue().getMaxNestedBlocks());
            measuredMethod.setHasJavaDocs(entry.getValue().getHasJavadoc());
            measuredMethod.setSourceLinesOfCode(entry.getValue().getLoc());
            measuredMethod.setParametersCount(entry.getValue().getParametersQty());
            measuredMethod.setFanIn(entry.getValue().getFanin());
            measuredMethod.setFanOut(entry.getValue().getFanout());
            measuredMethods.add(measuredMethod);
        }
        return measuredMethods;
    }
}
