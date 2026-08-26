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

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final VirtualCoinRepository virtualCoinRepository;
    private final ArtistRepository artistRepository;
    private final BookingRepository bookingRepository;

    @Override
    public Page<TransactionResponse> getAllTransactions(Pageable pageable) {
        log.info("Fetching all transactions with pagination: {}", pageable);
        return transactionRepository.findAll(pageable)
                .map(TransactionResponse::new);
    }

    @Override
    public Page<TransactionResponse> getAllLoggedInArtistTransaction(Pageable pageable, TransactionType transactionType, TransactionPurpose transactionPurpose) {
        log.info("Fetching transactions for logged-in artist with type: {} and purpose: {} with pagination: {}", transactionType, transactionPurpose, pageable);
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        VirtualCoin virtualCoin = virtualCoinRepository.findVirtualCoinByArtist(artist)
                .orElseThrow(() -> new RuntimeException("Virtual coin not found for artist: " + artist.getId()));

        Page<Transaction> transactionPage = transactionRepository.findAllByVirtualCoinAndTransactionTypeAndTransactionPurpose(
                virtualCoin, transactionType, transactionPurpose, pageable
        ).orElseGet(() -> transactionRepository.findAll(pageable));

        return transactionPage.map(TransactionResponse::new);
    }

    @Override
    public TransactionResponse createTransaction(TransactionRequest transactionRequest) {
        log.info("Creating transaction for artist with ID: {}", transactionRequest.getArtistId());
        Artist artist = artistRepository.findById(transactionRequest.getArtistId())
                .orElseThrow(() -> new RuntimeException("Artist not found with ID: " + transactionRequest.getArtistId()));

        VirtualCoin virtualCoin = virtualCoinRepository.findVirtualCoinByArtist(artist).orElseThrow(
                () -> new RuntimeException("Virtual coin not found for artist with ID: " + artist.getId())
        );

        Transaction transaction = new Transaction();

        if (transactionRequest.getAmount() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        }

        Booking booking = bookingRepository.findById(transactionRequest.getBookingId())
                .orElse(null);

        assert booking != null;
        if (    booking.getId().equals(transactionRequest.getBookingId())
                && transactionRequest.getTransactionType().equals(TransactionType.CREDIT)
                && transactionRequest.getTransactionPurpose().equals(TransactionPurpose.BOOKING_PAYMENT
        )) {
            transaction.setTransactionType(TransactionType.CREDIT);
            transaction.setTransactionPurpose(TransactionPurpose.BOOKING_PAYMENT);

            virtualCoin.setBalance(virtualCoin.getBalance() + transactionRequest.getAmount());
            // Saving VirtualCoin after updating balance
            virtualCoinRepository.save(virtualCoin);

            transaction.setAmount(transactionRequest.getAmount());
            transaction.setVirtualCoin(virtualCoin);
            transaction.setBookingId(booking.getId());
            transaction.setStatus(Status.APPROVED);

            transactionRepository.save(transaction);
        } else if (transactionRequest.getTransactionType().equals(TransactionType.DEBIT)
                && transactionRequest.getTransactionPurpose().equals(TransactionPurpose.WITHDRAWAL_REQUEST)) {
            if (virtualCoin.getBalance() < transactionRequest.getAmount()) {
                throw new IllegalArgumentException("Insufficient balance for debit transaction.");
            }
            transaction.setTransactionType(TransactionType.DEBIT);
            transaction.setTransactionPurpose(TransactionPurpose.WITHDRAWAL_REQUEST);

            virtualCoin.setBalance(virtualCoin.getBalance() - transactionRequest.getAmount());
            // Saving VirtualCoin after updating balance
            virtualCoinRepository.save(virtualCoin);

            transaction.setAmount(transactionRequest.getAmount());
            transaction.setVirtualCoin(virtualCoin);
            transaction.setBookingId(booking.getId());

            transactionRepository.save(transaction);
        } else if (transactionRequest.getTransactionType().equals(TransactionType.DEBIT)
                && transactionRequest.getTransactionPurpose().equals(TransactionPurpose.SERVICE_FEE)) {
            if (virtualCoin.getBalance() < transactionRequest.getAmount()) {
                throw new IllegalArgumentException("Insufficient balance for service fee transaction.");
            }
            transaction.setTransactionType(TransactionType.DEBIT);
            transaction.setTransactionPurpose(TransactionPurpose.SERVICE_FEE);

            virtualCoin.setBalance(virtualCoin.getBalance() - transactionRequest.getAmount());
            // Saving VirtualCoin after updating balance
            virtualCoinRepository.save(virtualCoin);

            transaction.setAmount(transactionRequest.getAmount());
            transaction.setVirtualCoin(virtualCoin);
            transaction.setBookingId(booking.getId());

            transactionRepository.save(transaction);
        } else {
            throw new IllegalArgumentException("Invalid transaction type or purpose.");
        }

        log.info("Transaction created successfully for artist with ID: {}", artist.getId());
        return new TransactionResponse(transaction);
    }

    @Override
    public WithDrawResponse withDraw(WithDrawRequest withDrawRequest) {
        log.info("Processing withdrawal request for artist with ID: {}", loggedInUserUtil.getLoggedInArtist().getId());

        // Get the logged-in artist
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        Long artistId = artist.getId();

        // Fetch the artist's virtual coin
        VirtualCoin virtualCoin = virtualCoinRepository.findVirtualCoinByArtist(artist)
                .orElseThrow(() -> new RuntimeException("Virtual coin not found for artist: " + artistId));

        // Validate withdrawal amount
        if (withDrawRequest.getAmount() <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }
        if (virtualCoin.getBalance() < withDrawRequest.getAmount()) {
            throw new IllegalArgumentException("Insufficient balance for withdrawal.");
        }

        // Create a new transaction
        Transaction transaction = new Transaction();
        transaction.setTransactionType(TransactionType.DEBIT);
        transaction.setTransactionPurpose(TransactionPurpose.WITHDRAWAL_REQUEST);
        transaction.setStatus(Status.PENDING);
        transaction.setAmount(withDrawRequest.getAmount());
        transaction.setVirtualCoin(virtualCoin);

        // Save the transaction
        transactionRepository.save(transaction);

        // Create a response object
        WithDrawResponse response = new WithDrawResponse();
        response.setTransactionId(transaction.getTransactionId());
        response.setArtistName(artist.getUser().getFullName());
        response.setAmount(transaction.getAmount());
        response.setTransactionType(transaction.getTransactionType().name());
        response.setTransactionPurpose(transaction.getTransactionPurpose().name());
        response.setTransactionStatus(transaction.getStatus().name());

        // Notify the super admin (this can be implemented via email or notification service)
        log.info("Withdrawal request sent to super admin for approval.");

        return response;
    }
}
