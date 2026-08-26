package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
public class TransactionalController extends BaseController {
    private final TransactionService transactionService;

    @PreAuthorize(UserRole.ANY_ADMIN)
    @GetMapping
    public ResponseEntity<GlobalApiResponse> getAllTransactions(Pageable pageable) {
        return successResponse(
            transactionService.getAllTransactions(pageable),
            "Fetched all transactions successfully."
        );
    }

    @PreAuthorize("hasRole('ARTIST')")
    @GetMapping("/artist")
    public ResponseEntity<GlobalApiResponse> getAllLoggedInArtistTransaction(Pageable pageable, @RequestParam(defaultValue = "ALL") TransactionType transactionType, @RequestParam(defaultValue = "ALL") TransactionPurpose transactionPurpose) {
        return successResponse(
            transactionService.getAllLoggedInArtistTransaction(pageable, transactionType, transactionPurpose),
            "Fetched all transactions for logged-in artist successfully."
        );
    }

    @PreAuthorize("hasRole('ARTIST')")
    @PostMapping("/withDraw")
    public ResponseEntity<GlobalApiResponse> withDraw(@RequestBody WithDrawRequest withDrawRequest) {
        return successResponse(
            transactionService.withDraw(withDrawRequest),
            "Withdrawal request submitted successfully."
        );
    }


}
