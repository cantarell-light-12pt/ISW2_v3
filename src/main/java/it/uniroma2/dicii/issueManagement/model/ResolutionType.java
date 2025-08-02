package it.uniroma2.dicii.issueManagement.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ResolutionType {

    SOLVED("solved"),
    FIXED("fixed"),
    WON_T_FIX("won't fix");

    @Getter
    private final String resolution;

    /**
     * Restituisce l'istanza dell'enum ResolutionType corrispondente alla stringa di risoluzione fornita.
     * @param resolution La stringa di risoluzione da cercare
     * @return L'istanza dell'enum ResolutionType corrispondente, o null se non trovata
     */
    public static ResolutionType fromResolution(String resolution) {
        if (resolution == null) {
            return null;
        }
        for (ResolutionType type : ResolutionType.values()) {
            if (type.getResolution().equalsIgnoreCase(resolution)) {
                return type;
            }
        }
        return null;
    }
}