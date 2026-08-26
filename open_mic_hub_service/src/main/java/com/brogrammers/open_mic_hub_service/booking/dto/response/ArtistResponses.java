package com.brogrammers.open_mic_hub_service.booking.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtistResponses {
    private Long artistId;
    private String artistName;
    private URI artistImage;
    private String bio;
    private String stageName;


    public ArtistResponses(Artist artist, String fullName, String bio, URI fileUri, String stageName) {
        this.artistId = artist.getId();
        this.artistName = fullName;
        this.bio = bio;
        this.artistImage = fileUri;
        this.stageName = stageName;
    }

    public ArtistResponses(Artist artistId) {
        this.artistId = artistId.getId();
        this.artistName = artistId.getUser().getFullName();
        this.bio = artistId.getBio();
        this.stageName = artistId.getStageName();
    }
}
