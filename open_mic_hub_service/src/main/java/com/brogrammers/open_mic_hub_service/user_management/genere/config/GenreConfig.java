package com.brogrammers.open_mic_hub_service.user_management.genere.config;

import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Genre;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.CategoryRepository;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.GenreRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GenreConfig {
    private final GenreRepository genreRepository;
    private final CategoryRepository categoryRepository;

    @PostConstruct
    public void initGenresAndCategories() {
        if (genreRepository.count() > 0) return;

        // Singer Genre
        Genre singer = new Genre();
        singer.setName("Singer");
        singer.setDescription("Artists who perform singing acts");
        singer.setSlug("singer");
        List<Category> singerCategories = Arrays.asList(
                new Category(null, "Pop Singer", "Performs pop music", true),
                new Category(null, "Jazz Singer", "Performs jazz music", true),
                new Category(null, "Rock Singer", "Performs rock music", true),
                new Category(null, "Classical Singer", "Performs classical music", true),
                new Category(null, "Folk Singer", "Performs folk music", true),
                new Category(null, "Rap Artist", "Performs rap/hip-hop music", true)
        );
        categoryRepository.saveAll(singerCategories);
        singer.setCategories(singerCategories);
        genreRepository.save(singer);

        // Standup Comedian Genre
        Genre comedian = new Genre();
        comedian.setName("Standup Comedian");
        comedian.setDescription("Artists who perform standup comedy");
        comedian.setSlug("standup-comedian");
        List<Category> comedianCategories = Arrays.asList(
                new Category(null, "Observational Comedy", "Comedy based on observations", true),
                new Category(null, "Satire", "Satirical comedy", true),
                new Category(null, "Improv Comedy", "Improvisational comedy", true),
                new Category(null, "Musical Comedy", "Comedy with music", true),
                new Category(null, "Dark Comedy", "Dark humor", true)
        );
        categoryRepository.saveAll(comedianCategories);
        comedian.setCategories(comedianCategories);
        genreRepository.save(comedian);

        // Poet Genre
        Genre poet = new Genre();
        poet.setName("Poet");
        poet.setDescription("Artists who perform poetry");
        poet.setSlug("poet");
        List<Category> poetCategories = Arrays.asList(
                new Category(null, "Spoken Word", "Performance poetry", true),
                new Category(null, "Haiku", "Short form poetry", true),
                new Category(null, "Narrative Poetry", "Storytelling poetry", true),
                new Category(null, "Lyrical Poetry", "Expressive poetry", true)
        );
        categoryRepository.saveAll(poetCategories);
        poet.setCategories(poetCategories);
        genreRepository.save(poet);

        // Instrumentalist Genre
        Genre instrumentalist = new Genre();
        instrumentalist.setName("Instrumentalist");
        instrumentalist.setDescription("Artists who play musical instruments");
        instrumentalist.setSlug("instrumentalist");
        List<Category> instrumentalistCategories = Arrays.asList(
                new Category(null, "Guitarist", "Performs with guitar", true),
                new Category(null, "Pianist", "Performs with piano", true),
                new Category(null, "Drummer", "Performs with drums", true),
                new Category(null, "Violinist", "Performs with violin", true),
                new Category(null, "Saxophonist", "Performs with saxophone", true)
        );
        categoryRepository.saveAll(instrumentalistCategories);
        instrumentalist.setCategories(instrumentalistCategories);
        genreRepository.save(instrumentalist);

        // Storyteller Genre
        Genre storyteller = new Genre();
        storyteller.setName("Storyteller");
        storyteller.setDescription("Artists who perform storytelling");
        storyteller.setSlug("storyteller");
        List<Category> storytellerCategories = Arrays.asList(
                new Category(null, "Folk Tales", "Traditional stories", true),
                new Category(null, "Personal Stories", "Personal experiences", true),
                new Category(null, "Mythology", "Mythological stories", true)
        );
        categoryRepository.saveAll(storytellerCategories);
        storyteller.setCategories(storytellerCategories);
        genreRepository.save(storyteller);

        // Magician Genre
        Genre magician = new Genre();
        magician.setName("Magician");
        magician.setDescription("Artists who perform magic tricks");
        magician.setSlug("magician");
        List<Category> magicianCategories = Arrays.asList(
                new Category(null, "Illusionist", "Performs illusions", true),
                new Category(null, "Mentalist", "Performs mind tricks", true),
                new Category(null, "Card Tricks", "Performs card magic", true)
        );
        categoryRepository.saveAll(magicianCategories);
        magician.setCategories(magicianCategories);
        genreRepository.save(magician);
    }
}