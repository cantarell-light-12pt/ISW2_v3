package it.uniroma2.dicii.issueManagement.proportion;

import it.uniroma2.dicii.vcsManagement.model.CommitInfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class FixVersionResolver {

    private FixVersionResolver() {
        // Prevent instantiation
    }

    /**
     * Risolve la Fix Version come nome release.
     * Stampa SOLO i ticket che non avevano già FV e a cui è stata stimata.
     *
     * @param ticketKey
     * @param commits
     * @param releaseDates
     * @param ticketHadFixed se != null, indica se il ticket aveva già FV (controllato dal chiamante)
     * @return releaseName stimata o null
     */
    public static String resolveFixVersion(String ticketKey,
                                           List<? extends CommitInfo> commits,
                                           Map<String, LocalDate> releaseDates,
                                           Boolean ticketHadFixed) {

        if (commits == null || commits.isEmpty()) return null;

        // Trova la data più recente del commit
        LocalDate latestCommitDate = commits.stream()
                .map(CommitInfo::getCommitDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        if (latestCommitDate == null) return null;

        // Trova la release più vecchia con releaseDate >= latestCommitDate
        for (Map.Entry<String, LocalDate> entry : releaseDates.entrySet()) {
            if (!entry.getValue().isBefore(latestCommitDate)) {

                // Log SOLO se il ticket non aveva già FV
                if (ticketHadFixed != null && !ticketHadFixed) {
                    System.out.printf("[FV STIMATA] Ticket %s → FV: %s%n", ticketKey, entry.getKey());
                }

                return entry.getKey();
            }
        }

        // Nessuna release trovata → opzionale: se vuoi puoi loggare i falliti
        return null;
    }
}
