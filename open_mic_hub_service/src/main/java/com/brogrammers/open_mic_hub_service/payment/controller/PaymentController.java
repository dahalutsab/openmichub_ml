package com.brogrammers.open_mic_hub_service.payment.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@Slf4j
public class PaymentController extends BaseController {
    private final PaymentService paymentService;

     @PreAuthorize(UserRole.ANY_ADMIN)
     @GetMapping
    public ResponseEntity<GlobalApiResponse> getAllPayments(Pageable pageable) {
        return successResponse(
            paymentService.getAllPayments(pageable),
            "Fetched all payments successfully."
        );
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/user")
    public ResponseEntity<GlobalApiResponse> getAllPaymentsByLoggedInUser(Pageable pageable) {
        return successResponse(
            paymentService.getAllPaymentsByLoggedInUser(pageable),
            "Fetched all payments for logged-in user successfully."
        );
    }

    @PreAuthorize(UserRole.ANY_BOOKER)
    @PostMapping("/booking")
    public ResponseEntity<String> bookArtist(@RequestParam Long bookingId, String paymentType) {
        // Plain and synchronous. Returning a Mono here ran the gateway call off the request thread,
        // where the security context is gone: any failure came back as an empty-bodied 401 and the
        // browser signed the user out. Failures now travel as exceptions to the global handler,
        // which gives them an honest status and a message worth reading.
        return ResponseEntity.ok(paymentService.bookArtist(bookingId, paymentType));
    }

    @Operation(
            summary = "Handle callback from payment gateway",
            description = "Settles a booking payment. The payment is verified server-to-server with Khalti; query parameters other than pidx are ignored."
    )
    @PostMapping("/callback")
    public ResponseEntity<String> khaltiCallback(@RequestParam String pidx) {
        // Only pidx is read. Any status/amount the browser carries back is ignored - the service
        // verifies the payment directly with Khalti.
        return paymentService.handleCallback(pidx);
    }

}
