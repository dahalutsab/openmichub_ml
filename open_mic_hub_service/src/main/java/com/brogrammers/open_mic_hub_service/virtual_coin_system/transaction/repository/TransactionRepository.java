package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.repository;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Page<Transaction>> findAllByVirtualCoinAndTransactionTypeAndTransactionPurpose(
        VirtualCoin virtualCoin,
        TransactionType transactionType,
        TransactionPurpose transactionPurpose,
        Pageable pageable
    );
}
