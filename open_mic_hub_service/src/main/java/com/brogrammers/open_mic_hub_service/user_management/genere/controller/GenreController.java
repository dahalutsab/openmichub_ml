package com.brogrammers.open_mic_hub_service.user_management.genere.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.request.GenreRequest;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.service.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

@RestController
@RequestMapping(API_BASE + "/genre")
@RequiredArgsConstructor
@Tag(name = "Genre Management", description = "APIs for managing genres and their categories")
public class GenreController extends BaseController {
    private final GenreService genreService;

    @Operation(
            summary = "Create a new genre",
            description = "Creates a new genre with its categories."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<GlobalApiResponse> createGenre(
            @RequestBody GenreRequest genreRequest) {
        GenreResponse response = genreService.saveGenre(genreRequest);
        return successResponse(response, "Genre created successfully");
    }

    @Operation(
            summary = "Get genre by ID",
            description = "Fetches a genre by its ID (only if active)."
    )
    @GetMapping("/{id}")
    public ResponseEntity<GlobalApiResponse> getGenreById(
            @Parameter(description = "ID of the genre", required = true)
            @PathVariable Long id) {
        GenreResponse response = genreService.getGenreById(id);
        return successResponse(response, "Genre fetched successfully");
    }

    @Operation(
            summary = "Get all genres",
            description = "Fetches all active genres with their active categories."
    )
    @GetMapping
    public ResponseEntity<GlobalApiResponse> getAllGenres() {
        List<GenreResponse> response = genreService.getAllGenres();
        return successResponse(response, "Genres fetched successfully");
    }

    @Operation(
            summary = "Update a genre",
            description = "Updates an existing genre and its categories."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<GlobalApiResponse> updateGenre(
            @Parameter(description = "ID of the genre to update", required = true)
            @PathVariable Long id,
            @RequestBody GenreRequest genreRequest) {
        GenreResponse response = genreService.updateGenre(id, genreRequest);
        return successResponse(response, "Genre updated successfully");
    }

    @Operation(
            summary = "Delete a genre (soft delete)",
            description = "Soft deletes a genre by setting its active flag to false."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<GlobalApiResponse> deleteGenre(
            @Parameter(description = "ID of the genre to delete", required = true)
            @PathVariable Long id) {
        genreService.deleteGenre(id);
        return successResponse(null, "Genre deleted successfully");
    }
}