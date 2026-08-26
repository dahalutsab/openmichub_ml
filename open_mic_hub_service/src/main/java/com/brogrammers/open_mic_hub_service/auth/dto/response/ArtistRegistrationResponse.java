package com.brogrammers.open_mic_hub_service.auth.dto.response;


import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.dto.response.RolesResponse;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ArtistRegistrationResponse {
    Long id;
    String fullName;
    String userEmail;
    URI profilePicture;
    List<RolesResponse> userRole;
    String phoneNumber;
    String location;
    LocalDateTime otpExpiryTime;
    List<GenreResponse> genre;

    public ArtistRegistrationResponse(Artist artist) {
        this.id = artist.getId();
        this.fullName = artist.getUser().getFullName();
        this.userEmail = artist.getUser().getEmailId();
        this.profilePicture = FileUrlUtil.getFileUri(artist.getUser().getProfileImage());
        this.userRole = artist.getUser().getRoles().stream()
                .map(role -> new RolesResponse(role.getName(), role.getDescription()))
                .toList();
        this.phoneNumber = artist.getUser().getPhoneNumber();
        this.location = artist.getUser().getLocation();
    }

    public ArtistRegistrationResponse(Artist artist, LocalDateTime otpExpiryTime) {
        this(artist);
        this.otpExpiryTime = otpExpiryTime;
    }
}
