package com.brogrammers.open_mic_hub_service.user_management.user.service;

import com.brogrammers.open_mic_hub_service.auth.dto.response.UserRegistrationResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.dto.response.UsersResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserRegistrationResponse getLoggedInUser();

    UserRegistrationResponse getUserById(Long userId);

    Page<UsersResponse> getAllUsers(UserRole userRole, Pageable pageable);
}
