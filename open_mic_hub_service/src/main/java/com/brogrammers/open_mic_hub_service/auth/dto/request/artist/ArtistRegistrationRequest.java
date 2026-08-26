package com.brogrammers.open_mic_hub_service.auth.dto.request.artist;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtistRegistrationRequest {
        @NotEmpty(message = "Full name must not be empty")
        private String fullName;

        @NotEmpty(message = "User email must not be empty")
        @Email(message = "Invalid email format")
        private String userEmail;

        @NotEmpty(message = "Password must not be empty")
        private String password;

        private MultipartFile profileImage;

        @NotEmpty(message = "Phone number must not be empty")
        private String phoneNumber;

        @NotEmpty(message = "Location must not be empty")
        private String location;

        @NotEmpty(message = "Genres must not be empty")
        private List<ArtistGenreRequest> genres;

        @NotEmpty(message = "Bio must not be empty")
        private String bio;

        @NotEmpty(message = "Stage name must not be empty")
        private String stageName;

}