package com.brogrammers.open_mic_hub_service.bot.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@RequiredArgsConstructor
public class ChatResponse {
    private String message;
    private String results;

    public ChatResponse(String message, String results) {
        this.message = message;
        this.results = results;
    }

}