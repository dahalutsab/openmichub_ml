package com.brogrammers.open_mic_hub_service.user_management.genere.repository;

import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Genre;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
    List<Genre> findAllByCategoriesIn(List<Category> genres);

    Optional<Genre> findByCategoriesContaining(Category category);

    /**
     * Active genres with their categories already attached.
     *
     * <p>{@code Genre.categories} is a lazy {@code @OneToMany} and the application runs with
     * {@code open-in-view: false}, so touching the collection after the repository call has
     * returned throws {@code LazyInitializationException} — which is what made
     * {@code GET /genre} answer 500 and left the registration form with no genres to show.
     *
     * <p>The entity graph loads the categories in the same query. A read-only transaction on the
     * service would also have fixed the exception, but would have left one extra query per genre.
     */
    @EntityGraph(attributePaths = "categories")
    List<Genre> findByActiveTrue();

    @EntityGraph(attributePaths = "categories")
    Optional<Genre> findWithCategoriesById(Long id);
}
