package com.brogrammers.open_mic_hub_service.user_management.user.role.repository;

import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolesRepository extends JpaRepository<Roles, Long> {
    Optional<Roles> findByName(String name);
}
