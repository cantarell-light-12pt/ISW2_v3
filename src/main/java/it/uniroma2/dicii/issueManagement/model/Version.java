package it.uniroma2.dicii.issueManagement.model;

import it.uniroma2.dicii.methods.model.ProjectMethod;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@RequiredArgsConstructor
public class Version {

    @NonNull
    private String id;

    @NonNull
    private String name;

    @NonNull
    private LocalDate releaseDate;

    @NonNull
    private Boolean released;

    private List<ProjectMethod> methods;

}
