package com.brogrammers.open_mic_hub_service.message.service;

import com.brogrammers.open_mic_hub_service.message.dto.response.ChatResponse;
import com.brogrammers.open_mic_hub_service.message.dto.response.UsersChatResponse;
import com.brogrammers.open_mic_hub_service.message.entity.Chat;
import com.brogrammers.open_mic_hub_service.message.repository.ChatRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImplementation implements ChatService {
    private final ChatRepository chatRepository;
    private final UserInfoRepository userInfoRepository;
    private final LoggedInUserUtil loggedInUserUtil;


    @Override
    public List<ChatResponse> getAllChats(String otherUserEmail) {
        var loggedInUser = loggedInUserUtil.getLoggedInUser();
        var otherUser = userInfoRepository.findByEmailId(otherUserEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + otherUserEmail));

        var chats = chatRepository.findAllBySenderAndRecipient(loggedInUser, otherUser);
        chats.addAll(chatRepository.findAllBySenderAndRecipient(otherUser, loggedInUser));
        chats.sort(Comparator.comparing(Chat::getTimestamp));
        return chats.stream()
                .map(ChatResponse::new)
                .toList();
    }

    @Override
    public Page<UsersChatResponse> getMyChats(Pageable pageable) {
        var loggedInUser = loggedInUserUtil.getLoggedInUser();
        var chats = chatRepository.findAllBySenderOrRecipient(loggedInUser, loggedInUser, pageable);

        return chats.map(chat -> {
            var otherUser = chat.getSender().equals(loggedInUser) ? chat.getRecipient() : chat.getSender();
            return new UsersChatResponse(otherUser, chat.getContent(), chat.getTimestamp());
        });
    }

    @Override
    public Page<UsersChatResponse> getAllContacts(Pageable pageable) {
        var loggedInUser = loggedInUserUtil.getLoggedInUser();
        var allContacts = userInfoRepository.findAllByEmailIdNot(loggedInUser.getEmailId(), pageable);

        var filteredList = allContacts.getContent().stream()
                .filter(user -> user.isActive() && user.isVerified())
                .map(user -> {
                    var lastChat = chatRepository.findTopBySenderOrRecipientOrderByTimestampDesc(loggedInUser, user);
                    String lastMessage = lastChat != null ? lastChat.getContent() : "";
                    LocalDateTime timestamp = lastChat != null ? lastChat.getTimestamp() : null;
                    return new UsersChatResponse(user, lastMessage, timestamp);
                })
                .toList();

        return new PageImpl<>(filteredList, pageable, allContacts.getTotalElements());
    }


}
