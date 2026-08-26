package com.brogrammers.open_mic_hub_service.message.repository;

import com.brogrammers.open_mic_hub_service.message.entity.Chat;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    List<Chat> findAllBySenderAndRecipient(UserEntity loggedInUser, UserEntity otherUser);

    Page<Chat> findAllBySenderOrRecipient(UserEntity loggedInUser, UserEntity loggedInUser1, Pageable pageable);

    List<Chat> findAllBySenderOrRecipientOrderByTimestampDesc(UserEntity sender, UserEntity recipient);

    Chat findTopBySenderOrRecipientOrderByTimestampDesc(UserEntity loggedInUser, UserEntity user);
}
