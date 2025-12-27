package it.uniroma2.dicii.metrics.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MeasuredMethod {

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
    private double churn;
    private double previousDefectCount;
    private int developerCount;

    // Smells metrics
    private int blockerSmellsCount;
    private int majorSmellsCount;
    private int mediumSmellsCount;
    private int minorSmellsCount;
    private int infoSmellsCount;

}
