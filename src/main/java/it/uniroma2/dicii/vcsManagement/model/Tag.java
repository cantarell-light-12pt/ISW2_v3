package it.uniroma2.dicii.vcsManagement.model;

import lombok.Data;

@Data
public class Tag {

    private final String tagId;
    private final String tagName;
    private final String associatedCommitId;

}
