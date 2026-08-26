package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TransactionResponse {
    private Long transactionId;
    private ArtistResponse artist;
    private Long bookingId;
    private Double amount;
    private String transactionType;
    private String transactionPurpose;

    public TransactionResponse(Transaction transaction){
        this.transactionId = transaction.getTransactionId();
        this.artist = new ArtistResponse(transaction.getVirtualCoin());
        this.bookingId = transaction.getBookingId();
        this.amount = transaction.getAmount();
        this.transactionType = transaction.getTransactionType().name();
        this.transactionPurpose = transaction.getTransactionPurpose().name();
    }
}
