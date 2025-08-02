package it.uniroma2.dicii.issueManagement.model;

import lombok.Data;

import java.util.List;

@Data
public class TicketFilter {

    private List<TicketStatus> statuses;
    private List<TicketType> types;
    private List<ResolutionType> resolutions;
    private List<String> fields;

}
