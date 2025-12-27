package it.uniroma2.dicii.metrics;

import it.uniroma2.dicii.metrics.model.MeasuredMethod;

import java.util.List;

public interface MetricsExtractor {

    /**
     * Extracts the metrics from the repository using the default CK
     */
    List<MeasuredMethod> extractMetrics();

    /**
     * Extracts the metrics from the repository using the CK with the specified parameters
     *
     * @param useJars            specifies if Jars should be used
     * @param maxAtOnce          max number of elements to analyze at once
     * @param variablesAndFields specifies if variables and fields should be analyzed
     */
    List<MeasuredMethod> extractMetrics(Boolean useJars, Integer maxAtOnce, Boolean variablesAndFields);

}
