package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.service;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.TransactionRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.TransactionResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.WithDrawResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionService {

    /** Returns reserved funds to the artist when a payout does not complete. */
    void releaseWithdrawalHold(Transaction transaction);

    Page<TransactionResponse> getAllTransactions(Pageable pageable);
    Page<TransactionResponse> getAllLoggedInArtistTransaction(Pageable pageable, TransactionType transactionType, TransactionPurpose transactionPurpose);
    TransactionResponse createTransaction(TransactionRequest transactionRequest);

    WithDrawResponse withDraw(WithDrawRequest withDrawRequest);
}
