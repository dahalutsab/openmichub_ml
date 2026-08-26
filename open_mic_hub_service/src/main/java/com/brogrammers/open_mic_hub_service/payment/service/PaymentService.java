package com.brogrammers.open_mic_hub_service.payment.service;

import aj.org.objectweb.asm.commons.Remapper;
import com.brogrammers.open_mic_hub_service.payment.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface PaymentService {
    Page<PaymentResponse> getAllPayments(Pageable pageable);
    Page<PaymentResponse> getAllPaymentsByLoggedInUser(Pageable pageable);

    Mono<String> bookArtist(Long bookingId, String paymentType);

    ResponseEntity<String> handleCallback(String pidx);
}
