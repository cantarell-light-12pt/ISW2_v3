package it.uniroma2.dicii.metrics;

import it.uniroma2.dicii.metrics.impl.CKMetricsExtractor;
import it.uniroma2.dicii.metrics.model.MetricsExtractorType;

public class MetricFactory {

    public static MetricsExtractor getMetricsExtractor(MetricsExtractorType type) {
        return switch (type) {
            case CK -> new CKMetricsExtractor();
        };
    }
}