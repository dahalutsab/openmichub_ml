package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TransactionResponse {
    private Long transactionId;
    private ArtistResponse artist;
    private Long bookingId;
    private Double amount;
    private String transactionType;
    private String transactionPurpose;

    /**
     * PENDING, APPROVED or DECLINED.
     *
     * <p>Its absence was not cosmetic. A withdrawal request and a withdrawal already paid out look
     * identical without it, so the admin table offered "pay out" on every row including settled
     * ones, and an artist could not tell whether their request had been actioned.
     */
    private String status;

    /** When it was raised. What makes a payout queue orderable, and an age visible. */
    private LocalDateTime createdDate;

    public TransactionResponse(Transaction transaction){
        this.transactionId = transaction.getTransactionId();
        this.artist = new ArtistResponse(transaction.getVirtualCoin());
        this.bookingId = transaction.getBookingId();
        this.amount = transaction.getAmount();
        this.transactionType = transaction.getTransactionType().name();
        this.transactionPurpose = transaction.getTransactionPurpose().name();
        this.status = transaction.getStatus() == null ? null : transaction.getStatus().name();
        this.createdDate = transaction.getCreatedDate();
    }
}
