package com.brogrammers.open_mic_hub_service.user_management.user.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class RolesResponse {
    private String role;
    private String description;

    public RolesResponse(Roles role) {
        this.role = role.getName();
        this.description = role.getDescription();
    }
}
