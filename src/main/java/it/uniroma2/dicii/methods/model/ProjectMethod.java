package it.uniroma2.dicii.methods.model;

import it.uniroma2.dicii.issueManagement.model.Version;
import lombok.Data;

import java.util.Map;

@Data
public class ProjectMethod {

    private String name;
    private String classPath;
    private Version version;

    // Maps of measures calculated on the method, keyed by measure name
    private Map<String, Object> measures;

}
