package com.brogrammers.open_mic_hub_service.user_management.genere.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Genre;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenreResponse {
    private Long id;

    private String name;

    private String description;

    private String slug;

    private List<CategoryResponse> categories = new ArrayList<>();

    public GenreResponse(Genre genre, List<Category> categories) {
        this.id = genre.getId();
        this.name = genre.getName();
        this.description = genre.getDescription();
        this.slug = genre.getSlug();
        if (categories != null) {
            for (Category category : categories) {
                this.categories.add(new CategoryResponse(category));
            }
        }
    }

    public GenreResponse(Genre genre) {
        this.id = genre.getId();
        this.name = genre.getName();
        this.description = genre.getDescription();
        this.slug = genre.getSlug();
        if (genre.getCategories() != null) {
            for (Category category : genre.getCategories()) {
                this.categories.add(new CategoryResponse(category));
            }
        }
    }

}
