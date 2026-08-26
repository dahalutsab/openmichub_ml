package com.brogrammers.open_mic_hub_service.common;


import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public class BaseController {

    public ResponseEntity<GlobalApiResponse> successResponse(Object data) {
        GlobalApiResponse response = new GlobalApiResponse(LocalDateTime.now(), "Success",
                data, HttpStatus.OK.name());
        return ResponseEntity.ok(response);
    }


    public ResponseEntity<GlobalApiResponse> successResponse(Object data, String message){
        GlobalApiResponse response = new GlobalApiResponse(
                LocalDateTime.now(),
                message,
                data,
                HttpStatus.OK.name()
        );
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<GlobalApiResponse> successResponse(Object data, String message, HttpStatus status){
        GlobalApiResponse response = new GlobalApiResponse(
                LocalDateTime.now(),
                message,
                data,
                status.name()
        );
        return ResponseEntity.status(status).body(response);
    }

    public ResponseEntity<GlobalErrorResponse> errorResponse(HttpStatus status, String message, Exception exception) {
        GlobalErrorResponse response = new GlobalErrorResponse(LocalDateTime.now(), message, exception.getMessage(), status.name());
        response.setMessage(message);
        response.setError(exception.getMessage());
        return ResponseEntity.status(status).body(response);
    }
}

