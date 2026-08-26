package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.service;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.TransactionRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.TransactionResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.WithDrawResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.repository.TransactionRepository;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.service.VirtualCoinService;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final VirtualCoinRepository virtualCoinRepository;
    private final VirtualCoinService virtualCoinService;
    private final ArtistRepository artistRepository;
    private final BookingRepository bookingRepository;

    @Override
    public Page<TransactionResponse> getAllTransactions(Pageable pageable) {
        log.info("Fetching all transactions with pagination: {}", pageable);
        return transactionRepository.findAll(pageable)
                .map(TransactionResponse::new);
    }

    /**
     * The logged-in artist's own ledger. {@code ALL} means "no filter" for that dimension.
     *
     * <p>This previously fell back to {@code findAll} when the artist had no matching rows, which
     * returned every transaction on the platform to any artist with an empty ledger.
     */
    @Override
    public Page<TransactionResponse> getAllLoggedInArtistTransaction(Pageable pageable,
                                                                     TransactionType transactionType,
                                                                     TransactionPurpose transactionPurpose) {
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        VirtualCoin virtualCoin = virtualCoinService.getOrCreateWallet(artist);

        TransactionType type = transactionType == TransactionType.ALL ? null : transactionType;
        TransactionPurpose purpose = transactionPurpose == TransactionPurpose.ALL ? null : transactionPurpose;

        return transactionRepository.findForWallet(virtualCoin, type, purpose, pageable)
                .map(TransactionResponse::new);
    }

    /**
     * Records a ledger entry and moves the artist's balance by the same amount.
     *
     * <p>This is the only place in the application that mutates a wallet balance. Booking credits
     * are idempotent per booking, so a replayed payment callback cannot pay an artist twice.
     */
    @Transactional
    @Override
    public TransactionResponse createTransaction(TransactionRequest transactionRequest) {
        if (transactionRequest.getAmount() == null || transactionRequest.getAmount() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        }

        Artist artist = artistRepository.findById(transactionRequest.getArtistId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Artist not found with ID: " + transactionRequest.getArtistId()));

        VirtualCoin virtualCoin = virtualCoinService.getOrCreateWallet(artist);

        TransactionType type = transactionRequest.getTransactionType();
        TransactionPurpose purpose = transactionRequest.getTransactionPurpose();
        if (type == null || purpose == null || type == TransactionType.ALL || purpose == TransactionPurpose.ALL) {
            throw new IllegalArgumentException("A concrete transaction type and purpose are required.");
        }

        Long bookingId = transactionRequest.getBookingId();
        if (purpose == TransactionPurpose.BOOKING_PAYMENT) {
            if (bookingId == null) {
                throw new IllegalArgumentException("Booking payments require a booking id.");
            }
            if (!bookingRepository.existsById(bookingId)) {
                throw new EntityNotFoundException("Booking not found with ID: " + bookingId);
            }
            // Guard against a replayed gateway callback crediting the same booking twice.
            if (transactionRepository.existsByBookingIdAndTransactionPurpose(bookingId, purpose)) {
                log.warn("Booking {} has already been credited; skipping duplicate transaction.", bookingId);
                return new TransactionResponse(
                        transactionRepository.findFirstByBookingIdAndTransactionPurpose(bookingId, purpose));
            }
        }

        double amount = transactionRequest.getAmount();
        if (type == TransactionType.DEBIT && virtualCoin.getBalance() < amount) {
            throw new IllegalArgumentException("Insufficient balance for this transaction.");
        }

        virtualCoin.setBalance(type == TransactionType.CREDIT
                ? virtualCoin.getBalance() + amount
                : virtualCoin.getBalance() - amount);
        virtualCoinRepository.save(virtualCoin);

        Transaction transaction = new Transaction();
        transaction.setTransactionType(type);
        transaction.setTransactionPurpose(purpose);
        transaction.setAmount(amount);
        transaction.setVirtualCoin(virtualCoin);
        transaction.setBookingId(bookingId);
        transaction.setStatus(transactionRequest.getStatus() == null
                ? Status.APPROVED : transactionRequest.getStatus());
        transactionRepository.save(transaction);

        log.info("Recorded {} of {} for artist {} (purpose {}); balance now {}",
                type, amount, artist.getId(), purpose, virtualCoin.getBalance());
        return new TransactionResponse(transaction);
    }

    /**
     * Raises a withdrawal request and immediately reserves the amount.
     *
     * <p>The balance used to stay untouched until an admin approved the payout, so an artist could
     * file the same withdrawal repeatedly and every one passed the balance check. Debiting up front
     * makes the funds unavailable to a second request; a declined payout returns them.
     */
    @Transactional
    @Override
    public WithDrawResponse withDraw(WithDrawRequest withDrawRequest) {
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        VirtualCoin virtualCoin = virtualCoinService.getOrCreateWallet(artist);

        if (withDrawRequest.getAmount() <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }
        if (virtualCoin.getBalance() < withDrawRequest.getAmount()) {
            throw new IllegalArgumentException("Insufficient balance for withdrawal.");
        }

        virtualCoin.setBalance(virtualCoin.getBalance() - withDrawRequest.getAmount());
        virtualCoinRepository.save(virtualCoin);

        Transaction transaction = new Transaction();
        transaction.setTransactionType(TransactionType.DEBIT);
        transaction.setTransactionPurpose(TransactionPurpose.WITHDRAWAL_REQUEST);
        transaction.setStatus(Status.PENDING);
        transaction.setAmount(withDrawRequest.getAmount());
        transaction.setVirtualCoin(virtualCoin);
        transactionRepository.save(transaction);

        log.info("Artist {} requested withdrawal of {}; {} reserved, balance now {}",
                artist.getId(), withDrawRequest.getAmount(), withDrawRequest.getAmount(),
                virtualCoin.getBalance());

        WithDrawResponse response = new WithDrawResponse();
        response.setTransactionId(transaction.getTransactionId());
        response.setArtistName(artist.getUser().getFullName());
        response.setAmount(transaction.getAmount());
        response.setTransactionType(transaction.getTransactionType().name());
        response.setTransactionPurpose(transaction.getTransactionPurpose().name());
        response.setTransactionStatus(transaction.getStatus().name());
        return response;
    }

    /** Returns reserved funds to the artist when a payout does not complete. */
    @Transactional
    @Override
    public void releaseWithdrawalHold(Transaction transaction) {
        if (transaction.getStatus() == Status.DECLINED) {
            return;
        }
        VirtualCoin wallet = transaction.getVirtualCoin();
        wallet.setBalance(wallet.getBalance() + transaction.getAmount());
        virtualCoinRepository.save(wallet);
        transaction.setStatus(Status.DECLINED);
        transactionRepository.save(transaction);
        log.info("Released withdrawal hold of {} for artist {}",
                transaction.getAmount(), wallet.getArtist().getId());
    }

}
