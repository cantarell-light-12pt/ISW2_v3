package it.uniroma2.dicii.issueManagement.proportion;

import it.uniroma2.dicii.issueManagement.model.Ticket;
import it.uniroma2.dicii.issueManagement.model.Version;

import java.util.List;

public interface Proportion {

    Version getInjectedVersionByProportion(List<Ticket> tickets, Ticket ticket);
}
