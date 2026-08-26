package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.repository;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Transactions for one wallet, optionally narrowed by type and purpose.
     *
     * <p>A {@code null} type or purpose means "no filter". The previous derived query took the
     * {@code ALL} enum member literally and matched nothing, and its caller fell back to returning
     * every transaction on the platform.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.virtualCoin = :virtualCoin
              AND (:type IS NULL OR t.transactionType = :type)
              AND (:purpose IS NULL OR t.transactionPurpose = :purpose)
            """)
    Page<Transaction> findForWallet(@Param("virtualCoin") VirtualCoin virtualCoin,
                                    @Param("type") TransactionType type,
                                    @Param("purpose") TransactionPurpose purpose,
                                    Pageable pageable);

    boolean existsByBookingIdAndTransactionPurpose(Long bookingId, TransactionPurpose transactionPurpose);

    Transaction findFirstByBookingIdAndTransactionPurpose(Long bookingId, TransactionPurpose transactionPurpose);
}
