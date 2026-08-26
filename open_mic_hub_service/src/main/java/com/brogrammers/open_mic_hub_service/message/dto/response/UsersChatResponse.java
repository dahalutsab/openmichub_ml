package com.brogrammers.open_mic_hub_service.message.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsersChatResponse {
    private String fullName;
    private String email;
    private URI profile;
    private String lastMessage;
    private LocalDateTime lastMessageTime;

    public UsersChatResponse(UserEntity userEntity) {
        this.fullName = userEntity.getFullName();
        this.email = userEntity.getEmailId();
        this.profile = FileUrlUtil.getFileUri(userEntity.getProfileImage());
    }

    public UsersChatResponse(UserEntity otherUser, String content, LocalDateTime timestamp) {
        this.fullName = otherUser.getFullName();
        this.email = otherUser.getEmailId();
        this.profile = FileUrlUtil.getFileUri(otherUser.getProfileImage());
        this.lastMessage = content;
        this.lastMessageTime = timestamp;
    }
}
