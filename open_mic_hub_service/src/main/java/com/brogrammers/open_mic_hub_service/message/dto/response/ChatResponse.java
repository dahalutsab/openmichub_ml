package com.brogrammers.open_mic_hub_service.message.dto.response;

import com.brogrammers.open_mic_hub_service.message.entity.Chat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatResponse {
    private String senderEmail;
    private String recipientEmail;
    private String content;
    private LocalDateTime timestamp;

    public ChatResponse(Chat chat) {
        this.senderEmail = chat.getSender().getEmailId();
        this.recipientEmail = chat.getRecipient().getEmailId();
        this.content = chat.getContent();
        this.timestamp = chat.getTimestamp();
    }
}