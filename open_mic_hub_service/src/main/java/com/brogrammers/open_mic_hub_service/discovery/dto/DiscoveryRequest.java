package com.brogrammers.open_mic_hub_service.discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the organizer is looking for, in the shape the ML service expects. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryRequest {

    private String query;
    private String city;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("budget_per_hour")
    private Double budgetPerHour;

    private String genre;
    private Integer limit;
}
