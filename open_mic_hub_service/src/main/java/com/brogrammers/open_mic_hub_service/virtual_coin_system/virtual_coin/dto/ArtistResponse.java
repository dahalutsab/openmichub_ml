package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.List;

@Data
@NoArgsConstructor
public class ArtistResponse {
    private String fullName;
    private String emailId;
    private URI profileImage;
    private List<Roles> roles;
    private boolean isVerified;
    private boolean isActive;
    private String stageName;
    private String bio;

    public ArtistResponse(Artist artist){
        this.fullName = artist.getUser().getFullName();
        this.emailId = artist.getUser().getEmailId();
        this.profileImage = FileUrlUtil.getFileUri(artist.getUser().getProfileImage());
        this.roles = artist.getUser().getRoles();
        this.isVerified = artist.getUser().isVerified();
        this.isActive = artist.getUser().isActive();
        this.stageName = artist.getStageName();
        this.bio = artist.getBio();
    }
}
