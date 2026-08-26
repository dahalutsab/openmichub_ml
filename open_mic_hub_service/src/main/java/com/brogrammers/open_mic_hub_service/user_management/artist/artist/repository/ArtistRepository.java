package com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, Long> {
    Optional<Artist> findByUser(UserEntity userEntity);

    /**
     * Verified artists with their categories attached.
     *
     * <p>{@code Artist.genres} is a lazy {@code @ManyToMany}; the entity graph loads it with the
     * page rather than one query per artist afterwards.
     */
    @EntityGraph(attributePaths = "genres")
    Page<Artist> findByUserVerifiedTrue(Pageable pageable);

    Optional<Artist> findByStageName(String stageName);

    List<Artist> findByStageNameContainingIgnoreCase(String artistName);

    @EntityGraph(attributePaths = "genres")
    Page<Artist> findByUserVerifiedTrueAndStageNameIgnoreCaseContaining(String searchTerm, Pageable pageable);
    @EntityGraph(attributePaths = "genres")
    Page<Artist> findByUserVerifiedTrueAndGenres_NameIgnoreCaseContaining(String genreName, Pageable pageable);
}
