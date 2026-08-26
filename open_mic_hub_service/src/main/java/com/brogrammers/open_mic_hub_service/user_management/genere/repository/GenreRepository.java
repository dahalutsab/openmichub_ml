package com.brogrammers.open_mic_hub_service.user_management.genere.repository;

import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
    List<Genre> findAllByCategoriesIn(List<Category> genres);

    Optional<Genre> findByCategoriesContaining(Category category);
}
