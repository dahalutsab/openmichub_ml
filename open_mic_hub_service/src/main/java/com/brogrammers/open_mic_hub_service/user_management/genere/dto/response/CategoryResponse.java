package com.brogrammers.open_mic_hub_service.user_management.genere.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;

    private String name;

    private String description;

    public CategoryResponse(Long id, String name, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public CategoryResponse(Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.description = category.getDescription();
    }
}
