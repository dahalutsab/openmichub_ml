package com.brogrammers.open_mic_hub_service.payment.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@Slf4j
public class PaymentController extends BaseController {
    private final PaymentService paymentService;

     @GetMapping
    public ResponseEntity<GlobalApiResponse> getAllPayments(Pageable pageable) {
        return successResponse(
            paymentService.getAllPayments(pageable),
            "Fetched all payments successfully."
        );
    }

    @GetMapping("/user")
    public ResponseEntity<GlobalApiResponse> getAllPaymentsByLoggedInUser(Pageable pageable) {
        return successResponse(
            paymentService.getAllPaymentsByLoggedInUser(pageable),
            "Fetched all payments for logged-in user successfully."
        );
    }

    @PostMapping("/booking")
    public Mono<ResponseEntity<String>> bookArtist(@RequestParam Long bookingId, String paymentType) {
        return paymentService.bookArtist(bookingId, paymentType)
                .map(response -> {
                    return ResponseEntity.ok().body(response); // Send the response to the user
                })
                .onErrorResume(error -> {
                    return Mono.just(ResponseEntity.status(500).body(null)); // Handle errors gracefully
                });
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
