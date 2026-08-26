package com.brogrammers.open_mic_hub_service.user_management.user.service.implementation;

import com.brogrammers.open_mic_hub_service.auth.dto.response.UserRegistrationResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.dto.response.UsersResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.service.UserService;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImplementation implements UserService {

    private final UserInfoRepository userInfoRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final RolesRepository rolesRepository;

    @Override
    public UserRegistrationResponse getLoggedInUser() {
        return new UserRegistrationResponse(loggedInUserUtil.getLoggedInUser());
    }

    @Override
    public UserRegistrationResponse getUserById(Long userId) {
        return userInfoRepository.findById(userId)
                .map(UserRegistrationResponse::new)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));
    }

    @Override
    public Page<UsersResponse> getAllUsers(UserRole userRole, Pageable pageable) {
        if (userRole == null) {
            return userInfoRepository.findAllByVerifiedTrue(pageable)
                    .map(UsersResponse::new);
        } else {
            Roles roles = rolesRepository.findByName(userRole.name())
                    .orElseThrow(() -> new EntityNotFoundException("Role not found: " + userRole.name()));
            return userInfoRepository.findAllByVerifiedTrueAndRoles(pageable, roles)
                    .map(UsersResponse::new);
        }
    }
}