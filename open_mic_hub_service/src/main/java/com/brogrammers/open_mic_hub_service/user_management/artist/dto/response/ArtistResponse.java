package com.brogrammers.open_mic_hub_service.user_management.artist.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Genre;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@AllArgsConstructor
@Data
public class ArtistResponse {
    private String fullName;
    private String bio;
    private URI profilePictureUrl;
    private String stageName;
    private double hourlyRate;
    private List<GenreResponse> genre;


    public ArtistResponse(Artist artist, List<GenreResponse> genreResponses) {
        this.fullName = artist.getUser().getFullName();
        this.bio = artist.getBio();
        this.profilePictureUrl = FileUrlUtil.getFileUri(artist.getUser().getProfileImage());
        this.stageName = artist.getStageName();
        this.hourlyRate = artist.getHourlyRate();
        this.genre = genreResponses;
    }

}