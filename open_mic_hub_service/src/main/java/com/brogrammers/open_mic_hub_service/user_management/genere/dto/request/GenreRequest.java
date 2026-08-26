package com.brogrammers.open_mic_hub_service.user_management.genere.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenreRequest {

    private String name;

    private String description;

    private String slug;

    private List<CategoryRequest> categories;

}
