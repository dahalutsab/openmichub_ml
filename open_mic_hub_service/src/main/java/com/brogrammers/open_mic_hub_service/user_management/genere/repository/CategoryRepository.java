package com.brogrammers.open_mic_hub_service.user_management.genere.repository;

import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
