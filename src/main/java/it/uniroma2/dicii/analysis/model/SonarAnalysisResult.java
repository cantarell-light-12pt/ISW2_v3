package it.uniroma2.dicii.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SonarAnalysisResult {
    private String key;
    private String rule;
    private String severity;
    private String component;
    private int line;
    private String message;
    private String type;
}
