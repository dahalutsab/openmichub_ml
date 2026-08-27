package com.brogrammers.open_mic_hub_service.social_feed.repository;

import com.brogrammers.open_mic_hub_service.social_feed.entity.Posts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface PostRepository extends JpaRepository<Posts, Long> {
    /**
     * The whole feed, newest first.
     *
     * <p>The fetch graph is not decoration. PostResponse reads the images collection and reaches
     * through to the artist's user for the author, both lazy; without it the feed threw
     * LazyInitializationException the moment a post existed, and the only reason it had never been
     * seen is that the table was empty.
     */
    @EntityGraph(attributePaths = {"images", "artist", "artist.user"})
    Page<Posts> findAllByOrderByCreatedDateDesc(Pageable pageable);

    /**
     * One artist's posts, newest first.
     *
     * <p>The feed could only be read whole, so an artist's own posts could not be shown on their
     * profile without pulling every post on the platform and filtering client-side.
     */
    @EntityGraph(attributePaths = {"images", "artist", "artist.user"})
    Page<Posts> findByArtist_IdOrderByCreatedDateDesc(Long artistId, Pageable pageable);
}