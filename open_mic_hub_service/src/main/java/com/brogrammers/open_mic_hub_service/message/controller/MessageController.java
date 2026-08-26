package com.brogrammers.open_mic_hub_service.message.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.message.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

@RestController
@RequestMapping(API_BASE + "/messages")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Message APIs", description = "APIs for managing messages between users")
public class MessageController extends BaseController {

    private final ChatService chatService;

    @Operation(
            summary = "Get all messages with a specific user",
            description = "Returns all messages exchanged between the logged-in user and the specified user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Messages fetched successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{otherUserEmail}")
    public ResponseEntity<GlobalApiResponse> getMessage(
            @Parameter(description = "Email of the other user") @PathVariable String otherUserEmail) {
        return successResponse(chatService.getAllChats(otherUserEmail));
    }

    @Operation(
            summary = "Get all contacts",
            description = "Returns a paginated list of all contacts (users except the logged-in user)"
    )
    @ApiResponse(responseCode = "200", description = "Contacts fetched successfully")
    @GetMapping("/contacts")
    public ResponseEntity<GlobalApiResponse> getAllContacts(
            @Parameter(hidden = true) @PageableDefault Pageable pageable) {
        return successResponse(chatService.getAllContacts(pageable));
    }

    @Operation(
            summary = "Get all chat conversations for the logged-in user",
            description = "Returns a paginated list of all chat conversations for the logged-in user."
    )
    @ApiResponse(responseCode = "200", description = "Chats fetched successfully")
    @GetMapping
    public ResponseEntity<GlobalApiResponse> getMyChats(
            @Parameter(hidden = true) @PageableDefault Pageable pageable) {
        return successResponse(chatService.getMyChats(pageable));
    }
}