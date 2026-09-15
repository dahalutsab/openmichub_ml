package com.brogrammers.open_mic_hub_service.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private String query;
    private int total;

    /** Which ranker produced the ordering: the trained model, or the cold-start heuristic. */
    private String strategy;

    /**
     * Whether the caller's own searches, profile views and bookings shaped this list. False for an
     * anonymous visitor, and for a signed-in one whose history is still too thin to use.
     */
    private boolean personalized;

    private List<ArtistHit> results = new ArrayList<>();

    /**
     * Identifies this list, so a click on one of its artists can say which list and which position
     * it came from. Set by the API, not the ML service; null on the unranked fallback.
     */
    private UUID requestId;

    public static SearchResult empty(String query, String strategy) {
        SearchResult result = new SearchResult();
        result.setQuery(query);
        result.setStrategy(strategy);
        result.setTotal(0);
        return result;
    }
}
