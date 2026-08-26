package com.brogrammers.open_mic_hub_service.bot;

import com.brogrammers.open_mic_hub_service.bot.dto.ChatResponse;
import com.brogrammers.open_mic_hub_service.bot.service.ChatBotService;
import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

@RestController
@RequestMapping(API_BASE + "/chat")
@RequiredArgsConstructor
public class ChatController extends BaseController {

    private final ChatBotService chatBotService;

    @GetMapping
    @Cacheable(value = "chatResponses", key = "#request")
    public ResponseEntity<GlobalApiResponse> getChatResponse(@RequestParam String request) {
        ChatResponse chatResponse = chatBotService.getResponse(request);
        return successResponse(chatResponse, "Chat response fetched successfully.");
    }


}