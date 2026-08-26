package com.brogrammers.open_mic_hub_service.auth.dto.request.login;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

public record AuthRequest(
        @NotEmpty(message = "Email must not be empty")
        @Schema(example = "admin@openmichub.com")
        String email,
        @NotEmpty(message = "Password must not be empty")
        @Schema(example = "your-password")
        String password
) {
}
