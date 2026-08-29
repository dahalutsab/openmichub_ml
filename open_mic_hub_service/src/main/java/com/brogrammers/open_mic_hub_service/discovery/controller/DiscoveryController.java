package com.brogrammers.open_mic_hub_service.discovery.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.discovery.client.MlServiceClient;
import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryRequest;
import com.brogrammers.open_mic_hub_service.discovery.dto.SearchResult;
import com.brogrammers.open_mic_hub_service.discovery.service.InteractionService;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.ArtistService;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * <p>These endpoints are public, but not anonymous: a signed-in visitor's token is honoured when
 * one is sent, which does two things. What they search for and open is recorded, and the ranking
 * they get is personalised from that history plus their past bookings. An anonymous visitor is not
 * recorded, and gets the same ranking the platform served before any of this existed.
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
    private final LoggedInUserUtil loggedInUserUtil;
    private final InteractionService interactionService;

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

        Long userId = loggedInUserUtil.currentUserIdOrNull();
        DiscoveryRequest request = DiscoveryRequest.builder()
                .query(q)
                .city(city)
                .eventType(eventType)
                .budgetPerHour(budgetPerHour)
                .genre(genre)
                .limit(Math.min(Math.max(limit, 1), MAX_LIMIT))
                .userId(userId)
                .build();

        // Recorded after the request is built, so this search informs the next one rather than
        // itself. It runs on another thread and cannot delay or fail the response.
        interactionService.recordSearch(userId, q, genre, city, eventType, budgetPerHour);

        return mlServiceClient.search(request)
                .map(result -> successResponse(result, "Artists found"))
                .orElseGet(() -> fallback(q, limit));
    }

    @Operation(summary = "Recommended artists",
            description = "Ranked artists for a set of requirements, with no text query. "
                    + "Used for browse and filter surfaces. Personalised from the caller's own "
                    + "searches, profile views and past bookings when they are signed in.")
    @GetMapping("/recommendations")
    public ResponseEntity<GlobalApiResponse> recommendations(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Double budgetPerHour,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "20") int limit) {

        Long userId = loggedInUserUtil.currentUserIdOrNull();
        DiscoveryRequest request = DiscoveryRequest.builder()
                .city(city)
                .eventType(eventType)
                .budgetPerHour(budgetPerHour)
                .genre(genre)
                .limit(Math.min(Math.max(limit, 1), MAX_LIMIT))
                .userId(userId)
                .build();

        interactionService.recordBrowse(userId, genre, city, eventType, budgetPerHour);

        return mlServiceClient.recommend(request)
                .map(result -> successResponse(result, "Recommended artists"))
                .orElseGet(() -> fallback(null, limit));
    }

    @Operation(summary = "Artists similar to one artist",
            description = "Nearest neighbours in the embedding space the ML service maintains. "
                    + "Public, and returns an empty list if the ML service is unavailable.")
    @GetMapping("/artists/{artistId:\\d+}/similar")
    public ResponseEntity<GlobalApiResponse> similarArtists(
            @PathVariable long artistId,
            @RequestParam(defaultValue = "6") int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return successResponse(
                java.util.Map.of("similar", mlServiceClient.similarArtists(artistId, capped)),
                "Similar artists fetched");
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
