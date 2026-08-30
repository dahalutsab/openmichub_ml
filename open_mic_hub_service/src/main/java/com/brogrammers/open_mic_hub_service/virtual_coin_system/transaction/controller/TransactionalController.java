package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status;
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

    /**
     * Withdrawal requests awaiting a decision, newest first.
     *
     * <p>Readable by any admin, because seeing the queue is not the same as paying it: only a
     * SUPER_ADMIN can act on a row, through {@code POST /api/v1/artist/withdraw} to pay out or
     * {@code /withdrawals/{id}/decline} to refuse.
     *
     * <p>{@code status=ALL} returns every withdrawal, settled ones included.
     */
    @PreAuthorize(UserRole.ANY_ADMIN)
    @GetMapping("/withdrawals")
    public ResponseEntity<GlobalApiResponse> getWithdrawalRequests(
            Pageable pageable, @RequestParam(defaultValue = "PENDING") String status) {
        Status wanted = "ALL".equalsIgnoreCase(status) ? null : Status.valueOf(status.toUpperCase());
        return successResponse(
            transactionService.getWithdrawalRequests(wanted, pageable),
            "Fetched withdrawal requests successfully."
        );
    }

    /** Refuses a pending withdrawal and returns the held funds. Moves money, so SUPER_ADMIN only. */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/withdrawals/{transactionId}/decline")
    public ResponseEntity<GlobalApiResponse> declineWithdrawal(@PathVariable Long transactionId) {
        return successResponse(
            transactionService.declineWithdrawal(transactionId),
            "Withdrawal declined and the funds returned to the artist."
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
