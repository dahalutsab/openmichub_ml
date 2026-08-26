package com.brogrammers.open_mic_hub_service.message.service;

import com.brogrammers.open_mic_hub_service.message.dto.response.ChatResponse;
import com.brogrammers.open_mic_hub_service.message.dto.response.UsersChatResponse;
import com.brogrammers.open_mic_hub_service.message.entity.Chat;
import com.brogrammers.open_mic_hub_service.message.repository.ChatRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
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
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImplementation implements ChatService {
    private final ChatRepository chatRepository;
    private final UserInfoRepository userInfoRepository;
    private final LoggedInUserUtil loggedInUserUtil;


    /**
     * Stores a message so it survives the socket connection.
     *
     * <p>Messages were only ever broadcast to connected clients and never written, so
     * {@code chatRepository.save} appeared nowhere in the codebase and every conversation reloaded
     * empty.
     */
    @Override
    public ChatResponse saveMessage(UserEntity sender, UserEntity recipient, String content) {
        Chat chat = new Chat();
        chat.setSender(sender);
        chat.setRecipient(recipient);
        chat.setContent(content);
        chat.setTimestamp(LocalDateTime.now());
        return new ChatResponse(chatRepository.save(chat));
    }

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

    /**
     * One entry per conversation, most recent first.
     *
     * <p>This used to page over raw messages, so the same person appeared once per message they had
     * exchanged rather than once as a conversation.
     */
    @Override
    public Page<UsersChatResponse> getMyChats(Pageable pageable) {
        var loggedInUser = loggedInUserUtil.getLoggedInUser();

        List<UsersChatResponse> conversations = chatRepository
                .findAllBySenderOrRecipientOrderByTimestampDesc(loggedInUser, loggedInUser)
                .stream()
                .collect(Collectors.toMap(
                        chat -> chat.getSender().equals(loggedInUser)
                                ? chat.getRecipient().getId() : chat.getSender().getId(),
                        chat -> chat,
                        (newest, older) -> newest,
                        LinkedHashMap::new))
                .values().stream()
                .map(chat -> {
                    var otherUser = chat.getSender().equals(loggedInUser)
                            ? chat.getRecipient() : chat.getSender();
                    return new UsersChatResponse(otherUser, chat.getContent(), chat.getTimestamp());
                })
                .toList();

        int from = (int) Math.min(pageable.getOffset(), conversations.size());
        int to = Math.min(from + pageable.getPageSize(), conversations.size());
        return new PageImpl<>(conversations.subList(from, to), pageable, conversations.size());
    }

    /**
     * Contactable users.
     *
     * <p>The active/verified filter now runs in the query. It used to be applied to the page after
     * the database had already paginated, so pages came back short and the reported total was wrong.
     */
    @Override
    public Page<UsersChatResponse> getAllContacts(Pageable pageable) {
        var loggedInUser = loggedInUserUtil.getLoggedInUser();
        var allContacts = userInfoRepository
                .findAllByEmailIdNotAndIsActiveTrueAndVerifiedTrue(loggedInUser.getEmailId(), pageable);

        var filteredList = allContacts.getContent().stream()
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
