package com.brogrammers.open_mic_hub_service.message.service;

import com.brogrammers.open_mic_hub_service.message.dto.response.ChatResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.message.dto.response.UsersChatResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatService {

    /** Stores a message so it survives the socket connection. */
    ChatResponse saveMessage(UserEntity sender, UserEntity recipient, String content);

    List<ChatResponse> getAllChats(String otherUserEmail);

    Page<UsersChatResponse> getMyChats(Pageable pageable);

    Page<UsersChatResponse> getAllContacts(Pageable pageable);
}
