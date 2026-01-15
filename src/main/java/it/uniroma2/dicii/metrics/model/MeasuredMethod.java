package it.uniroma2.dicii.metrics.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * This class represents the measured method.
 * It uses wrappers to initialize all fields to null within the constructor.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class MeasuredMethod {

    private MetricsExtractorType extractedFrom;

    private String methodName;

    // Complexity metrics
    private Integer cyclomaticComplexity;
    private Integer cognitiveComplexity;
    private Integer maxNestingDepth;
    private Boolean hasJavaDocs;

    // Size metrics
    private Integer sourceLinesOfCode;
    private Integer parametersCount;
    private Double commentDensity;

    // Coupling metrics
    private Integer fanIn;
    private Integer fanOut;

    // History metrics
    private Integer churn;
    private Integer defectCount;
    private Integer developerCount;

    // Smells metrics
    private Integer blockerSmellsCount;
    private Integer criticalSmellsCount;
    private Integer majorSmellsCount;
    private Integer minorSmellsCount;
    private Integer infoSmellsCount;

    public void incrementOrSetBlockerSmellsCount() {
        if (blockerSmellsCount == null) blockerSmellsCount = 0;
        else blockerSmellsCount++;
    }

    public void incrementOrSetCriticalSmellsCount() {
        if (criticalSmellsCount == null) criticalSmellsCount = 0;
        else criticalSmellsCount++;
    }

    public void incrementOrSetMajorSmellsCount() {
        if (majorSmellsCount == null) majorSmellsCount = 0;
        else majorSmellsCount++;
    }

    public void incrementOrSetMinorSmellsCount() {
        if (minorSmellsCount == null) minorSmellsCount = 0;
        else minorSmellsCount++;
    }

    public void incrementOrSetInfoSmellsCount() {
        if (infoSmellsCount == null) infoSmellsCount = 0;
        else infoSmellsCount++;
    }

    public void incrementOrSetDefectCount() {
        if (defectCount == null) defectCount = 0;
        else defectCount++;
    }

}