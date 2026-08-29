package com.brogrammers.open_mic_hub_service.auth.controller;

import com.brogrammers.open_mic_hub_service.auth.dto.request.CompleteProfileRequest;
import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.security.oauth.ProfileCompletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operations on the signed-in account.
 *
 * <p>Deliberately not under {@code /api/v1/auth}. That prefix is whitelisted wholesale — both in
 * the security configuration and again in the access-token filter, which skips parsing the token
 * entirely for anything on the list. An authenticated endpoint underneath it would never see a
 * caller: the filter would decline to identify anyone, and the security rule would then reject the
 * request as anonymous. Sitting outside that prefix, these are covered by the default
 * {@code anyRequest().authenticated()} and need no special case at all.
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account", description = "Operations on the signed-in account")
public class AccountController extends BaseController {

    private final ProfileCompletionService profileCompletionService;

    /**
     * Answers the one question a social sign-in leaves open: booking, or performing.
     *
     * <p>Accepted once per account. A second attempt is a conflict rather than a role change.
     */
    @Operation(
            summary = "Finish setting up a social sign-in account",
            description = "Choose whether the account books artists or performs. Artists supply a "
                    + "stage name and at least one style. Answerable once."
    )
    @PostMapping("/complete-profile")
    public ResponseEntity<GlobalApiResponse> completeProfile(
            @Valid @RequestBody CompleteProfileRequest request) {
        return successResponse(profileCompletionService.complete(request),
                "Account setup completed successfully");
    }

    /** Whether the signed-in account still owes that answer. */
    @Operation(summary = "Whether this account still needs setting up")
    @GetMapping("/complete-profile")
    public ResponseEntity<GlobalApiResponse> profileCompletionStatus() {
        return successResponse(Map.of("onboardingRequired", profileCompletionService.isPending()),
                "Account setup status fetched successfully");
    }
}
