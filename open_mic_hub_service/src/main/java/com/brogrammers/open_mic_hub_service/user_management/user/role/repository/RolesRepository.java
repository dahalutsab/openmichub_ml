package com.brogrammers.open_mic_hub_service.user_management.user.role.repository;

import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RolesRepository extends JpaRepository<Roles, Long> {

    Optional<Roles> findByName(String name);

    boolean existsByName(String name);

    /**
     * Renames a role in place.
     *
     * <p>Done as a direct update rather than by loading and saving the entity: the row is
     * referenced by id from {@code users_roles}, so renaming it preserves every assignment, and
     * bypassing the persistence context keeps the rename independent of whatever else is in the
     * session during startup.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Roles r SET r.name = :newName, r.description = :description WHERE r.name = :oldName")
    int renameRole(@Param("oldName") String oldName,
                   @Param("newName") String newName,
                   @Param("description") String description);
}
