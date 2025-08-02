package it.uniroma2.dicii.vcsManagement.model;

import it.uniroma2.dicii.issueManagement.model.Version;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Data
@RequiredArgsConstructor
public class CommitInfo {

    private final String commitId;
    private final String authorName;
    private final String authorEmail;
    private final LocalDate commitDate;
    private final String message;

    private Version version;

    @Override
    public String toString() {
        return String.format("%s: %s", commitId, message);
    }
}