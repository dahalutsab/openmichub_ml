package com.brogrammers.open_mic_hub_service.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long userId;
    private String fullName;
    private String emailId;
    private String phoneNumber;
    private URI profile;


}
