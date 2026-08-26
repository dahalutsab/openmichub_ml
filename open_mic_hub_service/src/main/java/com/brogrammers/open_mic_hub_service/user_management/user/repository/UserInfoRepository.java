package com.brogrammers.open_mic_hub_service.user_management.user.repository;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserInfoRepository extends JpaRepository<UserEntity,Long> {
    Optional<UserEntity> findByEmailId(String emailId);

    Page<UserEntity> findAllByEmailIdNot(String emailId, Pageable pageable);

    /** Contactable users: everyone except the caller who is active and has verified their email. */
    @Query("""
            SELECT u FROM UserEntity u
            WHERE u.emailId <> :emailId AND u.isActive = true AND u.verified = true
            """)
    Page<UserEntity> findAllByEmailIdNotAndIsActiveTrueAndVerifiedTrue(@Param("emailId") String emailId,
                                                                       Pageable pageable);

    Page<UserEntity> findAllByVerifiedTrue(Pageable pageable);

    Page<UserEntity> findAllByVerifiedTrueAndRoles(Pageable pageable, Roles roles);
}