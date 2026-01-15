package it.uniroma2.dicii.metrics;

import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CompositeMetricsExtractor implements MetricsExtractor {

    private final List<MetricsExtractor> extractors;

    public CompositeMetricsExtractor() {
        this.extractors = new ArrayList<>();
    }

    public void addExtractor(MetricsExtractor extractor) {
        this.extractors.add(extractor);
    }

    @Override
    public List<MeasuredMethod> extractMetrics() {
        Map<String, MeasuredMethod> mergedResults = new HashMap<>();

        // Run all extractors
        for (MetricsExtractor extractor : extractors) {
            log.info("Running extractor: {}", extractor.getClass().getSimpleName());
            List<MeasuredMethod> results = extractor.extractMetrics();

            // Handle null results
            if (results == null) {
                log.error("Extractor {} returned null results", extractor.getClass().getSimpleName());
                break;
            }

            for (MeasuredMethod method : results) {
                mergedResults.merge(method.getMethodName(), method, this::mergeMethods);
            }
        }
        return new ArrayList<>(mergedResults.values());
    }

    /**
     * Merges incoming data into the existing method object.
     * Logic: Overwrite ONLY if the existing value is null and the incoming value is non-null.
     */
    private MeasuredMethod mergeMethods(MeasuredMethod existing, MeasuredMethod incoming) {
        switch (incoming.getExtractedFrom()) {
            case SONAR: {
                existing.setBlockerSmellsCount(incoming.getBlockerSmellsCount());
                existing.setMajorSmellsCount(incoming.getMajorSmellsCount());
                existing.setCriticalSmellsCount(incoming.getCriticalSmellsCount());
                existing.setMinorSmellsCount(incoming.getMinorSmellsCount());
                existing.setInfoSmellsCount(incoming.getInfoSmellsCount());
                existing.setDefectCount(incoming.getDefectCount());
                break;
            }
            case CK: {
                existing.setCyclomaticComplexity(incoming.getCyclomaticComplexity());
                existing.setMaxNestingDepth(incoming.getMaxNestingDepth());
                existing.setHasJavaDocs(incoming.getHasJavaDocs());
                existing.setSourceLinesOfCode(incoming.getSourceLinesOfCode());
                existing.setParametersCount(incoming.getParametersCount());
                existing.setFanIn(incoming.getFanIn());
                existing.setFanOut(incoming.getFanOut());
                break;
            }
            case VCS: {
                existing.setChurn(incoming.getChurn());
                existing.setDeveloperCount(incoming.getDeveloperCount());
                break;
            }
            case JAVA_PARSER: {
                existing.setCognitiveComplexity(incoming.getCognitiveComplexity());
                existing.setCommentDensity(incoming.getCommentDensity());
                break;
            }
            default: {
                log.warn("Unknown source for metrics: {}", incoming.getExtractedFrom());
            }
        }
        return existing;
    }
}