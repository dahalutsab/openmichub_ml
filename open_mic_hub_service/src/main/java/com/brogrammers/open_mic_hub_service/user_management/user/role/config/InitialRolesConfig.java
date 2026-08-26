package com.brogrammers.open_mic_hub_service.user_management.user.role.config;

import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class InitialRolesConfig implements CommandLineRunner {

    private final RolesRepository rolesRepository;

    @Override
    public void run(String... args) {
        if (rolesRepository.count() < 3) {
            log.info("Roles creation request received");
            createRoleIfNotExists(UserRole.ADMIN.name(), "The superadmin of the application");
            createRoleIfNotExists(UserRole.USER.name(), "User of open mic hub");
            createRoleIfNotExists(UserRole.ARTIST.name(), "Artist of open mic hub");
            log.info("Roles created");
        } else {
            log.info("Roles already exist");
        }
    }

    private void createRoleIfNotExists(String name, String description) {
        if (rolesRepository.findByName(name).isEmpty()) {
            Roles role = Roles.builder()
                    .name(name)
                    .description(description)
                    .build();
            rolesRepository.save(role);
            log.info("Role {} inserted", name);
        }
    }
}