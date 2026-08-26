package com.brogrammers.open_mic_hub_service.social_feed.repository;

import com.brogrammers.open_mic_hub_service.social_feed.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    PostLike findByPostIdAndUserIdAndIsLikedIsTrue(Long postId, Long userId);
}
