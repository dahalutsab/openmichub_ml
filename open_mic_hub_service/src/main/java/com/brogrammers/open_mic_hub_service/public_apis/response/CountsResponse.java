package com.brogrammers.open_mic_hub_service.public_apis.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CountsResponse {
    private Long users;
    private Long artists;
    private Long bookings;
}
