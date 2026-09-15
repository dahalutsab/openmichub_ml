package com.brogrammers.open_mic_hub_service.discovery.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.discovery.client.MlServiceClient;
import com.brogrammers.open_mic_hub_service.discovery.dto.ArtistHit;
import com.brogrammers.open_mic_hub_service.discovery.dto.ClickRequest;
import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryActor;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

/**
 * Finding an artist.
 *
 * <p>Public, because browsing is what brings organizers to the platform in the
 * first place. Search runs meaning-based retrieval over artist profiles and then
 * a trained ranker orders the results by fit for the specific request — genre,
 * budget, location, rating and track record weighed together.
 *
 * <p>Public, but not anonymous. A signed-in visitor's token is honoured when one is sent, and a
 * visitor who has not signed in is recognised by the random id their browser keeps in the
 * {@code X-Visitor-Id} header. Either way, what they search for and open is recorded and the ranking
 * they get is personalised from it - plus past bookings for an account. Signing in moves a
 * browser's history onto the account. A request with neither is served the ordinary ranking and
 * recorded nowhere.
 *
 * <p>Every ranked list comes back with a {@code requestId}, and what was in it is logged, so a click
 * can be tied to the position it was chosen from.
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
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = DiscoveryActor.VISITOR_HEADER, required = false) String visitorId) {

        DiscoveryActor actor = actor(visitorId);
        DiscoveryRequest request = DiscoveryRequest.builder()
                .query(q)
                .city(city)
                .eventType(eventType)
                .budgetPerHour(budgetPerHour)
                .genre(genre)
                .limit(Math.min(Math.max(limit, 1), MAX_LIMIT))
                .userId(actor.userId())
                .visitorId(actor.visitorId())
                .build();

        // Recorded after the request is built, so this search informs the next one rather than
        // itself. It runs on another thread and cannot delay or fail the response.
        interactionService.recordSearch(actor, q, genre, city, eventType, budgetPerHour);

        return mlServiceClient.search(request)
                .map(result -> served(actor, result, "SEARCH", q, "Artists found"))
                .orElseGet(() -> fallback(q, limit));
    }

    @Operation(summary = "Recommended artists",
            description = "Ranked artists for a set of requirements, with no text query. "
                    + "Used for browse and filter surfaces. Personalised from the caller's own "
                    + "searches, profile views and past bookings - signed in, or by visitor id.")
    @GetMapping("/recommendations")
    public ResponseEntity<GlobalApiResponse> recommendations(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Double budgetPerHour,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = DiscoveryActor.VISITOR_HEADER, required = false) String visitorId) {

        DiscoveryActor actor = actor(visitorId);
        DiscoveryRequest request = DiscoveryRequest.builder()
                .city(city)
                .eventType(eventType)
                .budgetPerHour(budgetPerHour)
                .genre(genre)
                .limit(Math.min(Math.max(limit, 1), MAX_LIMIT))
                .userId(actor.userId())
                .visitorId(actor.visitorId())
                .build();

        interactionService.recordBrowse(actor, genre, city, eventType, budgetPerHour);

        boolean statedNothing = isBlank(city) && isBlank(eventType) && isBlank(genre) && budgetPerHour == null;
        return mlServiceClient.recommend(request)
                .map(result -> served(actor, result, statedNothing ? "HOME" : "BROWSE", null,
                        "Recommended artists"))
                .orElseGet(() -> fallback(null, limit));
    }

    @Operation(summary = "Record which result was chosen",
            description = "Posted by the client when an artist is opened from a ranked list. "
                    + "Kept only when that list was served to the same caller and contained that "
                    + "artist. Always answers 202; there is nothing for the caller to act on.")
    @PostMapping("/clicks")
    public ResponseEntity<GlobalApiResponse> click(
            @RequestBody ClickRequest click,
            @RequestHeader(value = DiscoveryActor.VISITOR_HEADER, required = false) String visitorId) {
        boolean kept = click != null && interactionService.recordClick(
                actor(visitorId), click.requestId(), click.artistId(), click.position());
        return successResponse(Map.of("recorded", kept), kept ? "Recorded" : "Ignored",
                HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Move this browser's history onto the signed-in account",
            description = "Called once, straight after signing in, with the visitor id the browser "
                    + "used while signed out. Requires an account.")
    @PostMapping("/visitor/claim")
    public ResponseEntity<GlobalApiResponse> claimVisitorHistory(
            @RequestHeader(value = DiscoveryActor.VISITOR_HEADER, required = false) String visitorId) {
        Long userId = loggedInUserUtil.getLoggedInUser().getId();
        int moved = interactionService.claimVisitorHistory(visitorId, userId);
        if (moved > 0) {
            mlServiceClient.forgetProfile(userId);
        }
        return successResponse(Map.of("moved", moved), "Visitor history claimed");
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
                Map.of("similar", mlServiceClient.similarArtists(artistId, capped)),
                "Similar artists fetched");
    }

    @Operation(summary = "Artists often chosen alongside one artist",
            description = "The artists people who booked or opened this one also booked or opened. "
                    + "Empty when there is not yet enough history to say.")
    @GetMapping("/artists/{artistId:\\d+}/also-chosen")
    public ResponseEntity<GlobalApiResponse> alsoChosen(
            @PathVariable long artistId,
            @RequestParam(defaultValue = "6") int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return successResponse(
                Map.of("alsoChosen", mlServiceClient.alsoChosen(artistId, capped)),
                "Artists chosen alongside fetched");
    }

    private DiscoveryActor actor(String visitorId) {
        return DiscoveryActor.of(loggedInUserUtil.currentUserIdOrNull(), visitorId);
    }

    /** Stamps a served list with an id, logs what was in it, and wraps it for the client. */
    private ResponseEntity<GlobalApiResponse> served(DiscoveryActor actor, SearchResult result,
                                                     String surface, String query, String message) {
        UUID requestId = UUID.randomUUID();
        result.setRequestId(requestId);
        List<Long> shown = result.getResults().stream()
                .map(ArtistHit::getArtistId)
                .filter(Objects::nonNull)
                .toList();
        interactionService.recordImpressions(actor, requestId, surface, query, shown, result.getStrategy());
        return successResponse(result, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
                Map.of("strategy", result.getStrategy(), "artists", page.getContent()),
                "Artists listed without ranking");
    }
}
