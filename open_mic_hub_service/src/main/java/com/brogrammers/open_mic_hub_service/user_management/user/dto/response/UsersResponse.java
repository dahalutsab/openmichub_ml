package com.brogrammers.open_mic_hub_service.user_management.user.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsersResponse {
    private Long id;

    private String fullName;

    private String email;

    private URI profileImage;

    private List<RolesResponse> roles;

    private boolean isActive;

    public UsersResponse(UserEntity user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmailId();
        this.profileImage = FileUrlUtil.getFileUri(user.getProfileImage());
        this.roles = user.getRoles().stream().map(RolesResponse::new).toList();
        this.isActive = user.isActive();
    }
}
