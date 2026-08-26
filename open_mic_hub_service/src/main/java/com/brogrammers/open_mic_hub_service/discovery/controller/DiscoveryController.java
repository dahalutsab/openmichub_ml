package com.brogrammers.open_mic_hub_service.discovery.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.discovery.client.MlServiceClient;
import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryRequest;
import com.brogrammers.open_mic_hub_service.discovery.dto.SearchResult;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.ArtistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

/**
 * Finding an artist.
 *
 * <p>Public, because browsing is what brings organizers to the platform in the
 * first place. Search runs meaning-based retrieval over artist profiles and then
 * a trained ranker orders the results by fit for the specific request — genre,
 * budget, location, rating and track record weighed together.
 *
 * <p>If the ML service is unreachable these fall back to the plain artist
 * listing, so discovery degrades rather than breaking.
 */
@RestController
@RequestMapping(API_BASE + "/discover")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Discovery", description = "Semantic artist search and ranked recommendations")
public class DiscoveryController extends BaseController {

    private static final int MAX_LIMIT = 50;

    private final MlServiceClient mlServiceClient;
    private final ArtistService artistService;

    @Operation(summary = "Search for artists",
            description = "Describe what you need in plain language. Results are retrieved by "
                    + "meaning, then ranked by fit for the event, budget and location supplied.")
    @GetMapping("/search")
    public ResponseEntity<GlobalApiResponse> search(
            @Parameter(description = "What you are looking for", example = "acoustic folk singer for a wedding")
            @RequestParam String q,
            @Parameter(description = "Only artists based in this city") @RequestParam(required = false) String city,
            @Parameter(description = "Wedding, Corporate, Festival, ...") @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Double budgetPerHour,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "20") int limit) {

        DiscoveryRequest request = DiscoveryRequest.builder()
                .query(q)
                .city(city)
                .eventType(eventType)
                .budgetPerHour(budgetPerHour)
                .genre(genre)
                .limit(Math.min(Math.max(limit, 1), MAX_LIMIT))
                .build();

        return mlServiceClient.search(request)
                .map(result -> successResponse(result, "Artists found"))
                .orElseGet(() -> fallback(q, limit));
    }

    @Operation(summary = "Recommended artists",
            description = "Ranked artists for a set of requirements, with no text query. "
                    + "Used for browse and filter surfaces.")
    @GetMapping("/recommendations")
    public ResponseEntity<GlobalApiResponse> recommendations(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Double budgetPerHour,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "20") int limit) {

        DiscoveryRequest request = DiscoveryRequest.builder()
                .city(city)
                .eventType(eventType)
                .budgetPerHour(budgetPerHour)
                .genre(genre)
                .limit(Math.min(Math.max(limit, 1), MAX_LIMIT))
                .build();

        return mlServiceClient.recommend(request)
                .map(result -> successResponse(result, "Recommended artists"))
                .orElseGet(() -> fallback(null, limit));
    }

    /**
     * Plain, unranked listing.
     *
     * <p>Used when the ML service cannot be reached. The response says so via
     * {@code strategy}, so a caller can tell a ranked list from a raw one.
     */
    private ResponseEntity<GlobalApiResponse> fallback(String query, int limit) {
        log.info("Serving the unranked artist listing as a fallback");
        var page = artistService.getAllVerifiedArtists(
                PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_LIMIT)), null, null);
        SearchResult result = SearchResult.empty(query, "unranked listing (ML service unavailable)");
        result.setTotal((int) page.getTotalElements());
        return successResponse(
                java.util.Map.of("strategy", result.getStrategy(), "artists", page.getContent()),
                "Artists listed without ranking");
    }
}
