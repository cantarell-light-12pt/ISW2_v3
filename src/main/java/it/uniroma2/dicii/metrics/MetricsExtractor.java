package it.uniroma2.dicii.metrics;

import it.uniroma2.dicii.metrics.model.MeasuredMethod;

import java.util.List;

public interface MetricsExtractor {

    /**
     * Extracts the metrics from the repository using the default CK
     */
    List<MeasuredMethod> extractMetrics();

}
