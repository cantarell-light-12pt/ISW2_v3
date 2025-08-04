package it.uniroma2.dicii.issueManagement.proportion;

import it.uniroma2.dicii.issueManagement.model.Ticket;
import it.uniroma2.dicii.issueManagement.model.Version;

import java.util.List;

public interface ProportionApplier {

    /**
     * Applies the complete proportion method to estimate the injected version for tickets with missing information.
     * <p>
     * This method first calculates the proportion between the time intervals (measured in project versions) as follows:
     * it evaluates the ratio between the number of versions elapsed from the injected version to the opening version,
     * and the number of versions elapsed from the opening version to the fix version. This proportion is computed
     * using only those tickets which already have an injected version specified.
     * </p>
     * <p>
     * Once the average proportion has been determined, the method uses this value to infer the injected version
     * for all tickets that do not have this information. For each such ticket, the injected version is estimated based on:
     * the opening version, the fix version, and the previously calculated average proportion. As a result,
     * all tickets — regardless of initially missing data — will have their injected versions assigned in a consistent
     * and reproducible manner according to the proportion technique.
     * </p>
     *
     * @param tickets the list of tickets to process; for each ticket, the method may update its injected version if missing
     */
    void applyProportions(List<Ticket> tickets);

}