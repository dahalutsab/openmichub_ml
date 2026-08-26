package com.brogrammers.open_mic_hub_service.virtual_coin_system;

import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.TransactionRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.repository.TransactionRepository;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.service.TransactionServiceImpl;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.service.VirtualCoinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the arithmetic that moves real money. A bug here previously wiped an artist's accumulated
 * earnings on every new booking payment, so these cases are worth pinning down.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceImplTest {

    private static final long ARTIST_ID = 7L;
    private static final long BOOKING_ID = 42L;

    @Mock private TransactionRepository transactionRepository;
    @Mock private LoggedInUserUtil loggedInUserUtil;
    @Mock private VirtualCoinRepository virtualCoinRepository;
    @Mock private VirtualCoinService virtualCoinService;
    @Mock private ArtistRepository artistRepository;
    @Mock private BookingRepository bookingRepository;

    @InjectMocks private TransactionServiceImpl service;

    private Artist artist;
    private VirtualCoin wallet;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(3L);
        user.setFullName("Test Artist");
        user.setEmailId("artist@example.com");

        artist = new Artist();
        artist.setId(ARTIST_ID);
        artist.setUser(user);

        wallet = new VirtualCoin();
        wallet.setVirtualCoinId(1L);
        wallet.setArtist(artist);
        wallet.setBalance(50_000.0);

        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
        when(virtualCoinService.getOrCreateWallet(artist)).thenReturn(wallet);
        when(bookingRepository.existsById(BOOKING_ID)).thenReturn(true);
        when(loggedInUserUtil.getLoggedInArtist()).thenReturn(artist);
    }

    private TransactionRequest bookingCredit(double amount) {
        TransactionRequest request = new TransactionRequest();
        request.setArtistId(ARTIST_ID);
        request.setBookingId(BOOKING_ID);
        request.setAmount(amount);
        request.setTransactionType(TransactionType.CREDIT);
        request.setTransactionPurpose(TransactionPurpose.BOOKING_PAYMENT);
        request.setStatus(Status.APPROVED);
        return request;
    }

    @Test
    @DisplayName("a booking credit adds to existing earnings instead of replacing them")
    void creditAccumulates() {
        service.createTransaction(bookingCredit(1_900.0));

        assertThat(wallet.getBalance()).isEqualTo(51_900.0);
    }

    @Test
    @DisplayName("crediting the same booking twice does not pay the artist twice")
    void bookingCreditIsIdempotent() {
        Transaction existing = new Transaction();
        existing.setAmount(1_900.0);
        existing.setVirtualCoin(wallet);
        existing.setTransactionType(TransactionType.CREDIT);
        existing.setTransactionPurpose(TransactionPurpose.BOOKING_PAYMENT);
        existing.setStatus(Status.APPROVED);
        when(transactionRepository.existsByBookingIdAndTransactionPurpose(
                BOOKING_ID, TransactionPurpose.BOOKING_PAYMENT)).thenReturn(true);
        when(transactionRepository.findFirstByBookingIdAndTransactionPurpose(
                BOOKING_ID, TransactionPurpose.BOOKING_PAYMENT)).thenReturn(existing);

        service.createTransaction(bookingCredit(1_900.0));

        assertThat(wallet.getBalance()).isEqualTo(50_000.0);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("a debit larger than the balance is refused")
    void debitBeyondBalanceIsRefused() {
        TransactionRequest request = bookingCredit(60_000.0);
        request.setTransactionType(TransactionType.DEBIT);
        request.setTransactionPurpose(TransactionPurpose.SERVICE_FEE);
        request.setBookingId(null);

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        assertThat(wallet.getBalance()).isEqualTo(50_000.0);
    }

    @Test
    @DisplayName("a withdrawal request reserves the amount immediately")
    void withdrawalReservesFunds() {
        WithDrawRequest request = new WithDrawRequest();
        request.setAmount(20_000.0);

        service.withDraw(request);

        assertThat(wallet.getBalance()).isEqualTo(30_000.0);
    }

    @Test
    @DisplayName("a second withdrawal cannot re-spend funds already reserved")
    void concurrentWithdrawalsCannotOverdraw() {
        WithDrawRequest first = new WithDrawRequest();
        first.setAmount(40_000.0);
        service.withDraw(first);

        WithDrawRequest second = new WithDrawRequest();
        second.setAmount(40_000.0);

        assertThatThrownBy(() -> service.withDraw(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        assertThat(wallet.getBalance()).isEqualTo(10_000.0);
    }

    @Test
    @DisplayName("a declined payout returns the reserved funds")
    void declinedPayoutReleasesHold() {
        Transaction hold = new Transaction();
        hold.setAmount(20_000.0);
        hold.setVirtualCoin(wallet);
        hold.setStatus(Status.PENDING);
        wallet.setBalance(30_000.0);

        service.releaseWithdrawalHold(hold);

        assertThat(wallet.getBalance()).isEqualTo(50_000.0);
        assertThat(hold.getStatus()).isEqualTo(Status.DECLINED);
    }
}
