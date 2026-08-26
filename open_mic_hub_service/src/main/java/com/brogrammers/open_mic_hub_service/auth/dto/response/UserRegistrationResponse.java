package com.brogrammers.open_mic_hub_service.auth.dto.response;


import com.brogrammers.open_mic_hub_service.user_management.user.dto.response.RolesResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
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
public class UserRegistrationResponse {
    Long id;
    String fullName;
    String userEmail;
    URI profilePicture;
    List<RolesResponse> userRole;
    String phoneNumber;
    String location;
    LocalDateTime otpExpiryTime;

    public UserRegistrationResponse(UserEntity user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.userEmail = user.getEmailId();
        this.profilePicture = FileUrlUtil.getFileUri(user.getProfileImage());
        this.userRole = user.getRoles().stream()
                .map(RolesResponse::new)
                .toList();
        this.phoneNumber = user.getPhoneNumber();
        this.location = user.getLocation();
    }

    public UserRegistrationResponse(UserEntity user, LocalDateTime otpExpiryTime) {
        this(user);
        this.otpExpiryTime = otpExpiryTime;
    }
}
