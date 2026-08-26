package com.brogrammers.open_mic_hub_service.common.constants;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
public class GlobalErrorResponse {
    private LocalDateTime timestamp;
    private String message;
    private String error;
    private String httpStatus;

    public GlobalErrorResponse(LocalDateTime now, String message, String error, String name) {
        this.timestamp = now;
        this.message = message;
        this.error = error;
        this.httpStatus = name;
    }
}

