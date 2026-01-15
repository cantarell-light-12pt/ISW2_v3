package it.uniroma2.dicii.issueManagement.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Data
@RequiredArgsConstructor
public class Version {

    private final String id;

    private final String name;

    private final LocalDate releaseDate;

    private final boolean released;

    private final boolean overdue;

    private String commitId;

    /**
     * Compares two versions based on their semantic versioning.
     * E.g., 5.0.1 > 5.0.0 > 4.2.1 > 4.2.0 etc.
     * <p>
     * Only the major, minor, and patch versions are compared.
     * The version name is assumed to follow this format: MAJOR.MINOR.PATCH.
     *
     * @param other the other version to compare with
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     */
    public int compare(Version other) {
        String[] numbers = this.name.split("\\.");
        String[] otherNumbers = other.getName().split("\\.");
        if (numbers[0].compareTo(otherNumbers[0]) == 0) {
            if (numbers[1].compareTo(otherNumbers[1]) == 0) {
                return numbers[2].compareTo(otherNumbers[2]);
            } else return numbers[1].compareTo(otherNumbers[1]);
        } else return numbers[0].compareTo(otherNumbers[0]);
    }

}
