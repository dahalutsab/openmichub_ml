package com.brogrammers.open_mic_hub_service.user_management.genere.service;

import com.brogrammers.open_mic_hub_service.user_management.genere.dto.request.GenreRequest;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;

import java.util.List;

public interface GenreService {
    GenreResponse saveGenre(GenreRequest request);
    GenreResponse getGenreById(Long id);
    List<GenreResponse> getAllGenres();
    GenreResponse updateGenre(Long id, GenreRequest request);
    void deleteGenre(Long id);
}