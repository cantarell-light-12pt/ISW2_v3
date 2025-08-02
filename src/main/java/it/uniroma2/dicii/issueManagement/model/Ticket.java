package it.uniroma2.dicii.issueManagement.model;

import it.uniroma2.dicii.vcsManagement.model.CommitInfo;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
@RequiredArgsConstructor
public class Ticket {

    @NonNull
    private final String key;
    @NonNull
    private final LocalDate issueDate;
    @NonNull
    private final LocalDate closedDate;
    @NonNull
    private String id;
    @NonNull
    private TicketType type;

    @NonNull
    private TicketStatus status;

    @NonNull
    private String assignee;

    private ResolutionType resolution;

    private String summary;

    private Version injected;
    private Version opening;
    private Version fixed;
    private List<Version> affectedVersions;

    private List<CommitInfo> associatedCommits;

    public void setAssociatedCommits(List<CommitInfo> associatedCommits) {
        this.associatedCommits = associatedCommits;
        this.associatedCommits.sort(Comparator.comparing(CommitInfo::getCommitDate));
    }

    public void addCommit(CommitInfo commit) {
        if (this.associatedCommits == null) {
            this.associatedCommits = new ArrayList<>();
            this.associatedCommits.add(commit);
        }
        else this.associatedCommits.add(commit);
    }

    public CommitInfo getLastCommit() {
        if (this.associatedCommits != null && !this.associatedCommits.isEmpty())
            return this.associatedCommits.get(this.associatedCommits.size() - 1);
        else return null;
    }

}
