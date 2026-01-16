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
    private int cyclomaticComplexity;
    private int cognitiveComplexity;
    private int maxNestingDepth;
    private boolean hasJavaDocs;

    // Size metrics
    private int sourceLinesOfCode;
    private int parametersCount;
    private double commentDensity;

    // Coupling metrics
    private int fanIn;
    private int fanOut;

    // History metrics
    private int churn;
    private int defectCount;
    private int developerCount;

    // Smells metrics
    private int blockerSmellsCount = 0;
    private int criticalSmellsCount = 0;
    private int majorSmellsCount = 0;
    private int minorSmellsCount = 0;
    private int infoSmellsCount = 0;

    // Buggyness flag
    private boolean buggy;

    public void incrementBlockerSmellsCount() {
        blockerSmellsCount++;
    }

    public void incrementCriticalSmellsCount() {
        criticalSmellsCount++;
    }

    public void incrementMajorSmellsCount() {
        majorSmellsCount++;
    }

    public void incrementMinorSmellsCount() {
        minorSmellsCount++;
    }

    public void incrementInfoSmellsCount() {
        infoSmellsCount++;
    }

    public void incrementDefectCount() {
        defectCount++;
    }

    public String toCsvRow() {
        return methodName + "," +
                cyclomaticComplexity + "," +
                cognitiveComplexity + "," +
                maxNestingDepth + "," +
                (hasJavaDocs ? "1" : "0") + "," +
                sourceLinesOfCode + "," +
                parametersCount + "," +
                commentDensity + "," +
                fanIn + "," +
                fanOut + "," +
                churn + "," +
                defectCount + "," +
                developerCount + "," +
                blockerSmellsCount + "," +
                criticalSmellsCount + "," +
                majorSmellsCount + "," +
                minorSmellsCount + "," +
                infoSmellsCount + "," +
                (buggy ? "1" : "0");
    }

}