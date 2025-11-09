package it.uniroma2.dicii.issueManagement.ticket;

import it.uniroma2.dicii.issueManagement.model.Ticket;
import it.uniroma2.dicii.issueManagement.model.TicketFilter;

import java.util.List;

public interface TicketsManager {

    /**
     * Clears all ticket lists.
     */
    void clear();

    /**
     * Retrieves all project tickets
     */
    void retrieveTickets();

    /**
     * Retrieves all project tickets corresponding to the filter
     *
     * @param ticketFilter the ticket's filter
     */
    void retrieveTickets(TicketFilter ticketFilter);

    /**
     * Removes all tickets with no commits associated with them.
     */
    void removeTicketsWithNoCommits();

    /**
     * Removes all tickets with no fixed version.
     */
    void removeTicketsWithNoFixVersion();

    /**
     * Sets the fixed version to all tickets using the commits associated with each ticket
     */
    void setFixVersionToTickets();

    /**
     * Returns the retrieved project tickets
     * @return the retrieved project tickets
     */
    List<Ticket> getTickets();

}
