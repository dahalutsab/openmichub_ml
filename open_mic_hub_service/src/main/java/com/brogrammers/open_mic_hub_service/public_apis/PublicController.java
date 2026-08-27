package com.brogrammers.open_mic_hub_service.public_apis;


import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.public_apis.service.PublicService;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.ArtistService;
import com.brogrammers.open_mic_hub_service.user_management.artist.dto.response.ArtistResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/artists/{artistId:\\d+}")
    public ResponseEntity<GlobalApiResponse> getArtistById(@PathVariable Long artistId) {
        return successResponse(
                artistService.getArtistById(artistId),
                "Fetched artist successfully."
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
