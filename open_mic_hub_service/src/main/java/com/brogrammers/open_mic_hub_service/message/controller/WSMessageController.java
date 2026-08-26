package com.brogrammers.open_mic_hub_service.message.controller;

import com.brogrammers.open_mic_hub_service.message.dto.request.ChatRequest;
import com.brogrammers.open_mic_hub_service.message.dto.response.ChatResponse;
import com.brogrammers.open_mic_hub_service.message.service.ChatService;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
public class WSMessageController {

    private static final Logger log = LoggerFactory.getLogger(WSMessageController.class);

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private ChatService chatService;

    @MessageMapping("/chat.sendPrivateMessage")
    public void sendPrivateMessage(@Payload ChatRequest message, Principal principal) {
        log.info("Received private message from: {}, to: {}, content: {}", principal.getName(), message.getRecipientEmail(), message.getContent());

        UserEntity sender = userInfoRepository.findByEmailId(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + principal.getName()));
        UserEntity recipient = userInfoRepository.findByEmailId(message.getRecipientEmail())
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found: " + message.getRecipientEmail()));

        if (message.getContent() == null || message.getContent().isBlank()) {
            return;
        }

        // Persist before broadcasting. This was previously left to "a service" that did not exist,
        // so messages lived only for the duration of the socket connection.
        chatService.saveMessage(sender, recipient, message.getContent());

        ChatResponse response = ChatResponse.builder()
                .senderEmail(principal.getName())
                .recipientEmail(message.getRecipientEmail())
                .content(message.getContent())
                .timestamp(LocalDateTime.now())
                .build();

        // Check if recipient has an active session
        SimpUser recipientUser = simpUserRegistry.getUser(message.getRecipientEmail());
        if (recipientUser != null && !recipientUser.getSessions().isEmpty()) {
            log.info("Recipient {} has {} active sessions", message.getRecipientEmail(), recipientUser.getSessions().size());
            recipientUser.getSessions().forEach(session ->
                    log.info("Session ID: {}, Principal: {}", session.getId(), recipientUser.getPrincipal() != null ? recipientUser.getPrincipal().getName() : "none"));
        } else {
            log.warn("No active session found for recipient: {}", message.getRecipientEmail());
        }

        // Broadcast to recipient
        messagingTemplate.convertAndSendToUser(
                message.getRecipientEmail(),
                "/queue/private",
                response
        );
        log.info("Broadcasted to: /user/{}/queue/private", message.getRecipientEmail());

        // Broadcast to sender (for confirmation)
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/private",
                response
        );
        log.info("Broadcasted to sender: /user/{}/queue/private", principal.getName());
    }
}