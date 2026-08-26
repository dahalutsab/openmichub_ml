package com.brogrammers.open_mic_hub_service.user_management.genere.service.implementation;

import com.brogrammers.open_mic_hub_service.user_management.genere.dto.request.CategoryRequest;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.request.GenreRequest;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.CategoryResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Genre;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.CategoryRepository;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.GenreRepository;
import com.brogrammers.open_mic_hub_service.user_management.genere.service.GenreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenreServiceImplementation implements GenreService {
    private final GenreRepository genreRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    @Override
    public GenreResponse saveGenre(GenreRequest request) {
        Genre genre = new Genre();
        genre.setName(request.getName());
        genre.setDescription(request.getDescription());
        genre.setSlug(request.getSlug());
        genre.setActive(true);

        List<Category> categories = request.getCategories() != null
                ? request.getCategories().stream()
                .map(this::toCategoryEntity)
                .collect(Collectors.toList())
                : List.of();

        categories.forEach(category -> category.setActive(true));
        categoryRepository.saveAll(categories);

        genre.setCategories(categories);
        Genre saved = genreRepository.save(genre);
        return toGenreResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public GenreResponse getGenreById(Long id) {
        Optional<Genre> genreOpt = genreRepository.findWithCategoriesById(id);
        if (genreOpt.isEmpty() || !genreOpt.get().isActive()) {
            throw new RuntimeException("Genre not found or inactive");
        }
        return toGenreResponse(genreOpt.get());
    }

    /**
     * Active genres, categories included.
     *
     * <p>The active filter is a query predicate rather than a Java stream so the database does not
     * hand back rows that are immediately discarded.
     */
    @Transactional(readOnly = true)
    @Override
    public List<GenreResponse> getAllGenres() {
        return genreRepository.findByActiveTrue().stream()
                .map(this::toGenreResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public GenreResponse updateGenre(Long id, GenreRequest request) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Genre not found"));
        if (!genre.isActive()) throw new RuntimeException("Genre is inactive");

        genre.setName(request.getName());
        genre.setDescription(request.getDescription());
        genre.setSlug(request.getSlug());

        // Optionally update categories
        if (request.getCategories() != null) {
            List<Category> categories = request.getCategories().stream()
                    .map(this::toCategoryEntity)
                    .collect(Collectors.toList());
            categories.forEach(category -> category.setActive(true));
            categoryRepository.saveAll(categories);
            genre.setCategories(categories);
        }

        Genre updated = genreRepository.save(genre);
        return toGenreResponse(updated);
    }

    @Transactional
    @Override
    public void deleteGenre(Long id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Genre not found"));
        genre.setActive(false);
        genreRepository.save(genre);
        // Optionally, also set categories inactive
        if (genre.getCategories() != null) {
            genre.getCategories().forEach(category -> {
                category.setActive(false);
                categoryRepository.save(category);
            });
        }
    }

    // --- Helper methods ---
    private Category toCategoryEntity(CategoryRequest req) {
        Category category = new Category();
        category.setName(req.getName());
        category.setDescription(req.getDescription());
        category.setActive(true);
        return category;
    }

    private GenreResponse toGenreResponse(Genre genre) {
        List<CategoryResponse> categoryResponses = genre.getCategories() != null
                ? genre.getCategories().stream()
                .filter(Category::isActive)
                .map(cat -> new CategoryResponse(cat.getId(), cat.getName(), cat.getDescription()))
                .collect(Collectors.toList())
                : List.of();
        return new GenreResponse(
                genre.getId(),
                genre.getName(),
                genre.getDescription(),
                genre.getSlug(),
                categoryResponses
        );
    }
}