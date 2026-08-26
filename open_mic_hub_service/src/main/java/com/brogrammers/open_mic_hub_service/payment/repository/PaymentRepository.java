package com.brogrammers.open_mic_hub_service.payment.repository;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPidx(String pidx);
    Optional<Page<Payment>> findAllByUserInfoEntity(UserEntity userInfoEntity, Pageable pageable);

    List<Payment> findByBookingId(Booking bookingId);
}
