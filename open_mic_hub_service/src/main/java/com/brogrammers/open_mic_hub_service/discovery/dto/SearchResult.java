package com.brogrammers.open_mic_hub_service.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private String query;
    private int total;

    /** Which ranker produced the ordering: the trained model, or the cold-start heuristic. */
    private String strategy;

    private List<ArtistHit> results = new ArrayList<>();

    public static SearchResult empty(String query, String strategy) {
        SearchResult result = new SearchResult();
        result.setQuery(query);
        result.setStrategy(strategy);
        result.setTotal(0);
        return result;
    }
}
