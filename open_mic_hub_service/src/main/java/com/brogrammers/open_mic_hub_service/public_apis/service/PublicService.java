package com.brogrammers.open_mic_hub_service.public_apis.service;

import com.brogrammers.open_mic_hub_service.public_apis.response.CountsResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;

import java.util.List;

public interface PublicService {
    CountsResponse getCounts();

    List<GenreResponse> getAllGenres();
}
