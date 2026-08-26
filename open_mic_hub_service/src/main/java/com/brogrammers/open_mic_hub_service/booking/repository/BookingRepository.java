package com.brogrammers.open_mic_hub_service.booking.repository;

import aj.org.objectweb.asm.commons.Remapper;
import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Page<Booking> findAllByUserId(UserEntity userId, Pageable pageable);

    Page<Booking> findAllByArtistIdAndStatus(Artist artist, BookingStatus status, Pageable pageable);
}
