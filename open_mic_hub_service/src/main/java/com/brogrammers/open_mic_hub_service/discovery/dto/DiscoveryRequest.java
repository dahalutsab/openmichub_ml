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

    /**
     * Who is asking, when that is known.
     *
     * <p>Null for an anonymous visitor, and the ML service ranks exactly as it did before when it
     * is. When it is set, the ranking is personalised from that person's own history.
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * The browser's random id, for a visitor who is not signed in. Their own searches and views
     * personalise the ranking the same way an account's do. Never sent alongside {@code userId}.
     */
    @JsonProperty("visitor_id")
    private String visitorId;
}
