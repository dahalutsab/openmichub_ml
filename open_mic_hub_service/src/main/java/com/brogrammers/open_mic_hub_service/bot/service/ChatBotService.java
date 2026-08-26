package com.brogrammers.open_mic_hub_service.bot.service;

import com.brogrammers.open_mic_hub_service.bot.dto.ChatResponse;

public interface ChatBotService {
    ChatResponse getResponse(String request);
}
