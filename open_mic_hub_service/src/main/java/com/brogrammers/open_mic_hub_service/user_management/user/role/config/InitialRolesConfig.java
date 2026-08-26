package com.brogrammers.open_mic_hub_service.user_management.user.role.config;

import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class InitialRolesConfig implements CommandLineRunner {

    private final RolesRepository rolesRepository;

    /**
     * Roles that were renamed, old name to new.
     *
     * <p>Renaming the existing row rather than inserting a new one keeps every {@code users_roles}
     * assignment intact, so accounts do not silently lose their access. The original ADMIN was
     * described in its own seed text as "the superadmin", and the original USER was the only role
     * that booked artists, so both map cleanly onto the new names.
     */
    private static final Map<String, UserRole> RENAMES = Map.of(
            "ADMIN", UserRole.SUPER_ADMIN,
            "USER", UserRole.ORGANIZER);

    @Override
    @Transactional
    public void run(String... args) {
        applyRenames();

        for (UserRole role : UserRole.values()) {
            createRoleIfNotExists(role);
        }
    }

    /**
     * Applies the renames once, before seeding.
     *
     * <p>Guarded so it is a no-op on a database that has already been migrated: the rename only
     * runs while the old name still exists and the new one does not.
     */
    private void applyRenames() {
        RENAMES.forEach((oldName, newRole) -> {
            if (!rolesRepository.existsByName(oldName)) {
                return;
            }
            if (rolesRepository.existsByName(newRole.name())) {
                log.warn("Both {} and {} exist; leaving the legacy role in place for manual review.",
                        oldName, newRole);
                return;
            }
            int renamed = rolesRepository.renameRole(oldName, newRole.name(), newRole.getDescription());
            if (renamed > 0) {
                log.info("Renamed role {} to {}; {} assignment(s) preserved.", oldName, newRole, renamed);
            }
        });
    }

    /** Inserts the role if it is new, and keeps its description in step with the enum otherwise. */
    private void createRoleIfNotExists(UserRole role) {
        rolesRepository.findByName(role.name()).ifPresentOrElse(existing -> {
            if (!role.getDescription().equals(existing.getDescription())) {
                existing.setDescription(role.getDescription());
                rolesRepository.save(existing);
                log.info("Refreshed description for role {}", role);
            }
        }, () -> {
            rolesRepository.save(Roles.builder()
                    .name(role.name())
                    .description(role.getDescription())
                    .build());
            log.info("Role {} inserted", role);
        });
    }
}
