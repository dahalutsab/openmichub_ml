package com.brogrammers.open_mic_hub_service.config.user;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class InsertInitialUserInfo implements CommandLineRunner {

    private final UserInfoRepository userInfoRepository;
    private final RolesRepository rolesRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String ADMIN_NAME = "OpenMicHub";

    @Value("${admin.email:admin@openmichub.com}")
    private String adminEmail;

    /**
     * Seed password for the initial admin. Intentionally has no default: if it is not supplied the
     * admin is not created, rather than falling back to a well-known password that ships in source.
     */
    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD is not set - skipping admin seeding. "
                    + "Set ADMIN_PASSWORD to create the initial admin account.");
            return;
        }
        if (userInfoRepository.findByEmailId(adminEmail).isEmpty()) {
            UserEntity admin = new UserEntity();
            admin.setFullName(ADMIN_NAME);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRoles(List.of(
                    rolesRepository.findByName(UserRole.ADMIN.toString())
                            .orElseThrow(() -> new RuntimeException("ADMIN Role not found"))
            ));
            admin.setEmailId(adminEmail);
            admin.setVerified(true);
            userInfoRepository.save(admin);
            log.info("Admin user inserted.");
        } else {
            log.info("Admin user already exists.");
        }
    }
}
