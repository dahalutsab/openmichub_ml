package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.repository;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status;
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

    /**
     * Withdrawal requests, optionally narrowed to one status. A {@code null} status means all.
     *
     * <p>The admin screen used to look for these by paging the whole ledger and filtering in the
     * browser. With three thousand transactions on the platform and a page size of a thousand, the
     * withdrawal rows — which are a fraction of a percent of the table and among the most recent —
     * fell outside the window every time, so the filter showed an empty table however many requests
     * were waiting.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.transactionPurpose = com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose.WITHDRAWAL_REQUEST
              AND (:status IS NULL OR t.status = :status)
            """)
    Page<Transaction> findWithdrawalRequests(@Param("status") Status status, Pageable pageable);

    boolean existsByBookingIdAndTransactionPurpose(Long bookingId, TransactionPurpose transactionPurpose);

    Transaction findFirstByBookingIdAndTransactionPurpose(Long bookingId, TransactionPurpose transactionPurpose);
}
