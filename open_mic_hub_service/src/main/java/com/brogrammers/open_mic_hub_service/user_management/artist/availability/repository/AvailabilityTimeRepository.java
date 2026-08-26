package com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository;

import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.AvailabilityTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityTimeRepository extends JpaRepository<AvailabilityTime, Long> {
}
