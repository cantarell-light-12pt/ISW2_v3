package it.uniroma2.dicii.metrics.model;

public enum MetricsExtractorType {

    // Metrics from the CK library
    CK,

    // Metrics from Sonar analysis
    SONAR,

    // Metrics from VCS
    VCS,

    // Metrics from the JavaParser library
    JAVA_PARSER
}