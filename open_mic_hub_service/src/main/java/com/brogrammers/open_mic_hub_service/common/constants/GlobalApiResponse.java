package com.brogrammers.open_mic_hub_service.common.constants;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class GlobalApiResponse {
    private LocalDateTime timestamp;
    private String message;
    private Object data;
    private String status;

    public GlobalApiResponse(LocalDateTime now, String success, Object data, String name) {
        this.timestamp = now;
        this.message = success;
        this.data = data;
        this.status = name;
    }
}