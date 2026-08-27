package com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.common.Auditable;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name="artists")
public class Artist extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private UserEntity user;

    @Column(unique = true, nullable = false)
    private String stageName;

    /**
     * The artist's public URL segment, derived from the stage name.
     *
     * <p>Separate from the id on purpose: the id stays the primary key that every foreign key and
     * internal call uses, while this is what a reader and a search engine see. Never reused, and
     * not changed when the stage name changes — an existing link should not rot.
     */
    @Column(unique = true, nullable = false, length = 160)
    private String slug;

    private String bio;

    private double hourlyRate;

    @OneToMany(mappedBy = "artistId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Booking> bookings = new ArrayList<>();
    @ManyToMany
    private List<Category> genres;

    // This field is used to store the average rating of the artist.
    private Double rating = 3.0;

}
