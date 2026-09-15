package com.brogrammers.open_mic_hub_service.public_apis;


import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.public_apis.service.PublicService;
import com.brogrammers.open_mic_hub_service.social_feed.service.PostService;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.ArtistService;
import com.brogrammers.open_mic_hub_service.user_management.artist.dto.response.ArtistResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.service.UserService;
import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryActor;
import com.brogrammers.open_mic_hub_service.discovery.service.InteractionService;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

@RestController
@RequestMapping(API_BASE + "/public")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public APIs", description = "APIs accessible without authentication")
public class PublicController extends BaseController {
    private final UserService userService;
    private final ArtistService artistService;
    private final PublicService publicService;
    private final PostService postService;
    private final InteractionService interactionService;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping("/artists")
    public ResponseEntity<GlobalApiResponse> getAllArtists(
            Pageable pageable,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String genreName) {
        return successResponse(
                artistService.getAllVerifiedArtists(pageable, searchTerm, genreName),
                "Fetched all artists successfully."
        );
    }

    /**
     * One artist, addressed either by slug or by id.
     *
     * <p>Deliberately a single mapping. Two — {@code {artistId:\\d+}} beside {@code {slug}} —
     * looks tidier and does not work: Spring cannot rank one as more specific than the other and
     * fails the request with "Ambiguous handler methods mapped for /api/v1/public/artists/201".
     * So the handle arrives as text and this decides.
     *
     * <p>Slugs are what public links use; the numeric form stays valid so links already shared,
     * and every internal caller that only knows an id, keep working.
     */
    @GetMapping("/artists/{handle}")
    public ResponseEntity<GlobalApiResponse> getArtist(
            @PathVariable String handle,
            @RequestHeader(value = DiscoveryActor.VISITOR_HEADER, required = false) String visitorId) {
        ArtistResponse artist = handle.matches("\\d+")
                ? artistService.getArtistById(Long.valueOf(handle))
                : artistService.getArtistBySlug(handle);

        // Who read whose profile is the signal discovery was missing: it is most of what a person
        // does before booking. Recorded against the account, or against the browser's visitor id
        // for someone not signed in, on another thread, and de-duplicated so a page that reloads
        // twice counts once.
        interactionService.recordProfileView(
                DiscoveryActor.of(loggedInUserUtil.currentUserIdOrNull(), visitorId),
                artist.getArtistId());

        return successResponse(artist, "Fetched artist successfully.");
    }

    /**
     * An artist's posts, for their public profile.
     *
     * <p>Under /public so it is readable without an account, like the profile itself. Paged with a
     * small default: the profile shows a strip, not an archive.
     */
    @GetMapping("/artists/{handle}/posts")
    public ResponseEntity<GlobalApiResponse> getArtistPosts(
            @PathVariable String handle,
            @PageableDefault(size = 6) Pageable pageable) {
        // Accepts either form for the same reason the profile does, so a page opened at a slug
        // does not have to resolve an id before it can ask for anything else.
        Long artistId = handle.matches("\\d+")
                ? Long.valueOf(handle)
                : artistService.getArtistBySlug(handle).getArtistId();

        return successResponse(
                postService.getPostsByArtist(artistId, pageable),
                "Fetched artist posts successfully."
        );
    }

    //get all genere
    @GetMapping("/genres")
    public ResponseEntity<GlobalApiResponse> getAllGenres() {
        return successResponse(publicService.getAllGenres(), "All genres fetched successfully.");
    }

    @GetMapping("/count")
    public ResponseEntity<GlobalApiResponse> getArtistCount() {
        return successResponse(publicService.getCounts(), "Total number of artists fetched successfully.");
    }


}
