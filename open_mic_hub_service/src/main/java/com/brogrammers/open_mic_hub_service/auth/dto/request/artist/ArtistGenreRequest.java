package com.brogrammers.open_mic_hub_service.auth.dto.request.artist;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtistGenreRequest {
        private Long genreId;
        List<Long> subGenreIds;
}
