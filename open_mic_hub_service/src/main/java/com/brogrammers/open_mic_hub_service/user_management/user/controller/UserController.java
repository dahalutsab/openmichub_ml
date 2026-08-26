package com.brogrammers.open_mic_hub_service.user_management.user.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.messages.UserSwaggerDocumentationMessage;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.service.UserService;
import com.brogrammers.open_mic_hub_service.util.messages.ResponseMessageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.brogrammers.open_mic_hub_service.user_management.user.messages.UserAPIConstants.API_USER;


@RestController
@RequestMapping(API_USER)
@RequiredArgsConstructor
@Slf4j
public class UserController extends BaseController {

    private static final String USER = "User";
    private final UserService userService;


    @Operation(
            summary = UserSwaggerDocumentationMessage.GET_LOGGED_IN_USER_SUMMARY,
            description = UserSwaggerDocumentationMessage.GET_LOGGED_IN_USER_DESCRIPTION
    )
    @GetMapping
    public ResponseEntity<GlobalApiResponse> getLoggedInUser(){
        return successResponse(userService.getLoggedInUser(), ResponseMessageUtil.fetchedSuccessfully(USER));
    }

    @Operation(
            summary = UserSwaggerDocumentationMessage.GET_USER_BY_ID_SUMMARY,
            description = UserSwaggerDocumentationMessage.GET_USER_BY_ID_DESCRIPTION
    )
    @GetMapping("/{userId}")
    public ResponseEntity<GlobalApiResponse> getUserById(@PathVariable Long userId){
        return successResponse(userService.getUserById(userId), ResponseMessageUtil.fetchedSuccessfully(USER));
    }

    @GetMapping("/all")
    public ResponseEntity<GlobalApiResponse> getAllUsers(
            @Parameter(
                    description = "User role to filter by (optional). If not provided, returns all verified users.",
                    schema = @Schema(implementation = UserRole.class)
            )
            @RequestParam(value = "userRole", required = false) UserRole userRole,
            @ParameterObject Pageable pageable
    ) {
        return successResponse(
                userService.getAllUsers(userRole, pageable),
                ResponseMessageUtil.fetchedSuccessfully(USER)
        );
    }


}