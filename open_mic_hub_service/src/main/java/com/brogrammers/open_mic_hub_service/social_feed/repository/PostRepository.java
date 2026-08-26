package com.brogrammers.open_mic_hub_service.social_feed.repository;

import com.brogrammers.open_mic_hub_service.social_feed.entity.Posts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface PostRepository extends JpaRepository<Posts, Long> {
    Page<Posts> findAllByOrderByCreatedDateDesc(Pageable pageable);
}