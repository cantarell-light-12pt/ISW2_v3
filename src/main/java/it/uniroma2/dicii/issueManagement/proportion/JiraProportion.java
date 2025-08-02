package it.uniroma2.dicii.issueManagement.proportion;

import it.uniroma2.dicii.issueManagement.model.Ticket;
import it.uniroma2.dicii.issueManagement.model.Version;
import it.uniroma2.dicii.vcsManagement.model.CommitInfo;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class JiraProportion {

    public static List<Ticket> applyProportion(
            List<Ticket> allTickets,
            List<Version> allReleases,
            Map<String, List<CommitInfo>> ticketCommitsMap,
            Map<String, LocalDate> releaseDates
    ) {
        int cutoffIndex = (int) Math.ceil(allReleases.size() * 0.33);
        List<Version> subsetReleases = allReleases.subList(0, cutoffIndex);
        Set<String> subsetReleaseNames = subsetReleases.stream()
                .map(Version::getName)
                .collect(Collectors.toSet());

        Map<Version, Integer> releaseIndexMap = new HashMap<>();
        for (int i = 0; i < allReleases.size(); i++) {
            releaseIndexMap.put(allReleases.get(i), i);
        }

        // --- 1️⃣ Calcolo Pmedia su ticket che sono NEL SUBSET e hanno IV e FV validi ---
        List<Ticket> ticketsForPmedia = allTickets.stream()
                .filter(t -> t.getInjected() != null && t.getFixed() != null
                        && subsetReleaseNames.contains(t.getFixed().getName()))
                .collect(Collectors.toList());

        List<Double> proportions = new ArrayList<>();
        for (Ticket t : ticketsForPmedia) {
            int iv = releaseIndexMap.get(t.getInjected());
            int fv = releaseIndexMap.get(t.getFixed());
            int ov = getOpeningVersionIndex(t, allReleases);
            if (fv > ov) {
                double p = (double) (fv - iv) / (fv - ov);
                proportions.add(p);
            }
        }

        double pMean = proportions.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);

        System.out.printf("\u2705 Pmedia calcolata su %d ticket completi: %.4f%n", proportions.size(), pMean);

        // --- 2️⃣ Stima FV per ticket che NON hanno FV e che sono nel subset ---
        List<Ticket> estimatedTicketsFV = new ArrayList<>();
        for (Ticket t : allTickets) {
            if (t.getFixed() == null) {
                List<? extends CommitInfo> commits = ticketCommitsMap.get(t.getKey());
                if (commits != null && !commits.isEmpty()) {
                    String fixVersionName = FixVersionResolver.resolveFixVersion(t.getKey(), commits, releaseDates, false);
                    if (fixVersionName != null && subsetReleaseNames.contains(fixVersionName)) {
                        Version matchedRelease = subsetReleases.stream()
                                .filter(r -> r.getName().equals(fixVersionName))
                                .findFirst().orElse(null);
                        if (matchedRelease != null) {
                            t.setFixed(matchedRelease);
                            estimatedTicketsFV.add(t);
                            System.out.printf("[FV STIMATA] Ticket %s → FV: %s%n", t.getKey(), matchedRelease.getName());
                        }
                    }
                }
            }
        }

        System.out.printf("\ud83e\udde0 FixVersionResolver ha stimato FV per %d ticket.%n", estimatedTicketsFV.size());

        // --- 3️⃣ Stima IV per TUTTI i ticket del subset che non hanno IV ---
        int ivStimate = 0;
        List<Ticket> ticketsNeedingIV = allTickets.stream()
                .filter(t -> subsetReleaseNames.contains(t.getFixed() != null ? t.getFixed().getName() : "")
                        && t.getInjected() == null)
                .collect(Collectors.toList());

        for (Ticket t : ticketsNeedingIV) {
            int fv = releaseIndexMap.get(t.getFixed());
            int ov = getOpeningVersionIndex(t, allReleases);
            int iv = (int) Math.round(fv - (fv - ov) * pMean);
            iv = Math.max(0, Math.min(iv, fv));
            t.setInjected(allReleases.get(iv));
            ivStimate++;
            System.out.printf("[IV STIMATA] Ticket %s → IV: %s%n", t.getKey(), allReleases.get(iv).getName());
        }

        System.out.printf("\ud83d\udccd IV stimata su %d ticket.%n", ivStimate);

        // --- 4️⃣ Ritorna i ticket che abbiamo effettivamente stimato IV ---
        return ticketsNeedingIV;
    }

    private static int getOpeningVersionIndex(Ticket ticket, List<Version> releases) {
        for (int i = 0; i < releases.size(); i++) {
            if (releases.get(i).getReleaseDate().isAfter(ticket.getIssueDate())) {
                return Math.max(0, i - 1);
            }
        }
        return releases.size() - 1;
    }
}
