package it.uniroma2.dicii.issueManagement.proportion;

import it.uniroma2.dicii.issueManagement.model.Ticket;
import it.uniroma2.dicii.issueManagement.model.Version;
import it.uniroma2.dicii.issueManagement.version.VersionsManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

@Slf4j
public class ProportionApplierImpl implements ProportionApplier {

    private final VersionsManager versionsManager;

    public ProportionApplierImpl(VersionsManager versionsManager) {
        this.versionsManager = versionsManager;
    }

    @Override
    public void applyProportions(List<Ticket> tickets) {
        List<Version> allVersions = versionsManager.getVersions();

        // Compute the proportion p for each complete ticket
        List<Double> proportions = new ArrayList<>();
        for (Ticket ticket : tickets) {
            Version iv = ticket.getInjected();
            Version ov = ticket.getOpening();
            Version fv = ticket.getFixed();

            if (iv != null && ov != null && fv != null) {
                int ovIdx = allVersions.indexOf(ov);
                int ivIdx = allVersions.indexOf(iv);
                int fvIdx = allVersions.indexOf(fv);

                if (ivIdx >= 0 && ovIdx >= 0 && fvIdx >= 0) {
                    int denom = fvIdx - ovIdx;
                    if (denom == 0) denom = 1;
                    int numer = ovIdx - ivIdx;
                    if (denom > 0 && numer >= 0) {
                        proportions.add((double) numer / denom);
                    }
                }
            }
            log.info("Ticket {}\tiv: {}\tov: {}\tfv: {}", ticket.getKey(), iv, ov, fv);
        }
        if (proportions.isEmpty())
            log.error("Unable to compute proportions. No tickets with injected version, opening version, and fixed version have been found.");

        OptionalDouble avgOpt = proportions.stream().mapToDouble(Double::doubleValue).average();
        if (avgOpt.isEmpty()) {
            return;
        }
        double avg = avgOpt.getAsDouble();

        log.info("Average proportion: {}", avg);

        // Estimate injected version for tickets missing it
        for (Ticket ticket : tickets) {
            if (ticket.getInjected() == null && ticket.getOpening() != null && ticket.getFixed() != null) {
                Version ov = ticket.getOpening();
                Version fv = ticket.getFixed();
                int ovIdx = allVersions.indexOf(ov);
                int fvIdx = allVersions.indexOf(fv);

                if (ovIdx >= 0 && fvIdx >= 0) {
                    int diff = fvIdx - ovIdx;
                    if (diff == 0) diff = 1;
                    int estimatedIdx = (int) Math.ceil(ovIdx - avg * diff);
                    estimatedIdx = Math.max(0, estimatedIdx);

                    // Ensure injected version is strictly before opening version
                    if (estimatedIdx > ovIdx) {
                        log.warn("Ticket {} has an injected version {} after the opening version {}. This is not allowed. The opening version will be used as injected version.", ticket.getKey(), ov.getName(), fv.getName());
                        estimatedIdx = ovIdx;
                    }
                    if (estimatedIdx >= allVersions.size()) {
                        log.warn("Ticket {} has an injected version {} after the last version {}. This is not allowed. The opening version will be used as injected version.", ticket.getKey(), ov.getName(), fv.getName());
                        estimatedIdx = ovIdx;
                    }
                    Version estimatedVersion = allVersions.get(estimatedIdx);
                    log.info("Estimated injected version: {} for ticket {}", estimatedVersion.getName(), ticket.getKey());
                    ticket.setInjected(estimatedVersion);
                }
            }
        }
    }
}